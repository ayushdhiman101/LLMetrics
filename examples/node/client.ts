/**
 * LLM Gateway — TypeScript client.
 *
 * Requirements: Node 20+
 * Install:      npm install
 * Run demo:     npx tsx demo.ts
 *
 * Option A — JWT auth (per-user provider keys):
 *   const gw = new GatewayClient("http://localhost:8080")
 *   await gw.login("alice@example.com", "secret123")   // once — token in memory for 24h
 *   const text = await gw.complete({ message: "Hello!", models: ["gemini-2.5-flash"] })
 *
 * Option B — JWT with token caching across process restarts:
 *   const gw = new GatewayClient("http://localhost:8080")
 *   await gw.ensureLoggedIn("alice@example.com", "secret123")  // only re-logins when expired
 *   const text = await gw.complete({ message: "Hello!", models: ["gemini-2.5-flash"] })
 *
 * Option C — API key auth (never expires, no login needed):
 *   const gw = new GatewayClient("http://localhost:8080", { apiKey: "llm-gateway-dev-key" })
 *   const text = await gw.complete({ message: "Hello!", models: ["gemini-2.5-flash"] })
 *   // Note: uses server-level provider keys, not per-user keys
 */

export type Provider = "openai" | "gemini" | "anthropic"
export type StatusEvent = "attempting" | "fallback" | "done"

export interface CompletionOptions {
  /** Raw text message. Provide this OR promptName, not both. */
  message?: string
  /**
   * Ordered fallback list. Gateway tries each in order until one succeeds.
   * OpenAI:    gpt-4o, gpt-4o-mini
   * Gemini:    gemini-2.5-flash, gemini-flash-latest
   * Anthropic: claude-sonnet-4-6, claude-haiku-4-5-20251001, claude-opus-4-7
   */
  models: string[]
  /** Named prompt template (from the Prompt Manager). */
  promptName?: string
  /** Specific version to use. Defaults to the active version. */
  version?: string
  /** Values for {{variable}} placeholders in the template. */
  variables?: Record<string, string>
  /** Called for each streamed token chunk. */
  onToken?: (token: string) => void
  /** Called for status events (attempting / fallback / done). */
  onStatus?: (event: StatusEvent, data: string) => void
}

export interface ProviderKey {
  provider: string
  configured: boolean
  updatedAt: string
}

export interface UsageSummary {
  totalRequests: number
  totalCostUsd: number
  byProvider: Array<{ provider: string; requests: number; inputTokens: number; outputTokens: number; costUsd: number }>
  byModel: Array<{ model: string; provider: string; requests: number; inputTokens: number; outputTokens: number; costUsd: number }>
  byDay: Array<{ day: string; requests: number; costUsd: number }>
}

export interface GatewayClientOptions {
  /** Static X-API-Key — never expires, no login required. Uses server-level provider keys. */
  apiKey?: string
}

export class GatewayClient {
  private baseUrl: string
  private apiKey: string | null
  private token: string | null = null

  constructor(baseUrl = "http://localhost:8080", opts: GatewayClientOptions = {}) {
    this.baseUrl = baseUrl.replace(/\/$/, "")
    this.apiKey = opts.apiKey ?? null
  }

  // ── Auth ───────────────────────────────────────────────────────────────────

  /**
   * Register a new account.
   * Does NOT log in automatically — call login() afterwards.
   * Throws GatewayError if the email is already registered (HTTP 409).
   */
  async register(email: string, password: string): Promise<void> {
    const res = await fetch(`${this.baseUrl}/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: email.trim().toLowerCase(), password }),
    })
    await raise(res)
  }

  /** Log in and store the JWT for all subsequent calls. Returns {token, userId, tenantId, email}. */
  async login(email: string, password: string): Promise<{ token: string; userId: string; tenantId: string; email: string }> {
    const res = await fetch(`${this.baseUrl}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email: email.trim().toLowerCase(), password }),
    })
    await raise(res)
    const data = await res.json()
    this.token = data.token
    return data
  }

  /**
   * Log in only when necessary — restores a cached token from disk if still valid,
   * otherwise re-authenticates and saves the new token.
   *
   * Call this once at the top of your script instead of login().
   * The token file is safe to .gitignore.
   */
  async ensureLoggedIn(email: string, password: string, tokenFile = ".gateway_token"): Promise<void> {
    const { readFileSync, writeFileSync, existsSync } = await import("fs")
    if (existsSync(tokenFile)) {
      const token = readFileSync(tokenFile, "utf8").trim()
      if (token && !tokenExpired(token)) {
        this.token = token
        return
      }
    }
    const data = await this.login(email, password)
    writeFileSync(tokenFile, data.token, "utf8")
  }

  /** Discard the JWT and notify the server (best-effort). */
  logout(): void {
    fetch(`${this.baseUrl}/auth/logout`, { method: "POST", headers: this.headers() }).catch(() => {})
    this.token = null
  }

  // ── Provider keys ──────────────────────────────────────────────────────────

  /** List which providers you have keys configured for (never returns the key itself). */
  async listProviderKeys(): Promise<ProviderKey[]> {
    const res = await fetch(`${this.baseUrl}/v1/user/provider-keys`, { headers: this.headers() })
    await raise(res)
    return res.json()
  }

  /**
   * Save or replace your API key for a provider.
   * Keys are encrypted with AES-256-GCM before storage.
   * Your key takes priority over the server's default for every request you make.
   */
  async setProviderKey(provider: Provider, apiKey: string): Promise<void> {
    const res = await fetch(`${this.baseUrl}/v1/user/provider-keys/${provider}`, {
      method: "PUT",
      headers: this.headers(),
      body: JSON.stringify({ apiKey }),
    })
    await raise(res)
  }

  /** Remove your stored key. Falls back to the server's environment variable. */
  async deleteProviderKey(provider: Provider): Promise<void> {
    const res = await fetch(`${this.baseUrl}/v1/user/provider-keys/${provider}`, {
      method: "DELETE",
      headers: this.headers(),
    })
    await raise(res)
  }

  // ── Completions ────────────────────────────────────────────────────────────

  /**
   * Stream a completion and return the full concatenated text.
   *
   * The gateway emits SSE events:
   *   attempting  — which model is being tried
   *   token       — one chunk of the response
   *   fallback    — switching to the next model in the chain
   *   done        — final model that served the response
   *   error       — unrecoverable failure
   */
  async complete(opts: CompletionOptions): Promise<string> {
    if (!opts.message && !opts.promptName) throw new Error("Provide message or promptName")

    const payload: Record<string, unknown> = { models: opts.models }
    if (opts.message)    payload.message    = opts.message
    if (opts.promptName) payload.promptName = opts.promptName
    if (opts.version)    payload.version    = opts.version
    if (opts.variables)  payload.variables  = opts.variables

    const res = await fetch(`${this.baseUrl}/v1/completions`, {
      method: "POST",
      headers: { ...this.headers(), Accept: "text/event-stream" },
      body: JSON.stringify(payload),
    })
    await raise(res)
    return consumeSSE(res, opts.onToken, opts.onStatus)
  }

  // ── Prompts ────────────────────────────────────────────────────────────────

  /** Create a new named prompt. */
  async createPrompt(name: string): Promise<{ id: string; name: string; tenantId: string; createdAt: string }> {
    const res = await fetch(`${this.baseUrl}/v1/prompts`, {
      method: "POST",
      headers: this.headers(),
      body: JSON.stringify({ name }),
    })
    await raise(res)
    return res.json()
  }

  /** List all versions for a prompt. */
  async listVersions(promptName: string): Promise<Array<{
    id: string; version: string; template: string; description: string | null; isActive: boolean; createdAt: string
  }>> {
    const res = await fetch(`${this.baseUrl}/v1/prompts/${promptName}/versions`, { headers: this.headers() })
    await raise(res)
    return res.json()
  }

  /**
   * Add a versioned template to a prompt.
   * Use {{variable_name}} placeholders — they are filled in at request time.
   */
  async addVersion(promptName: string, opts: {
    version: string
    template: string
    description?: string
    isActive?: boolean
  }): Promise<{ id: string; version: string; isActive: boolean }> {
    const res = await fetch(`${this.baseUrl}/v1/prompts/${promptName}/versions`, {
      method: "POST",
      headers: this.headers(),
      body: JSON.stringify({ isActive: true, ...opts }),
    })
    await raise(res)
    return res.json()
  }

  /** Switch the active version instantly — no redeployment needed. */
  async setActiveVersion(promptName: string, version: string): Promise<void> {
    const res = await fetch(`${this.baseUrl}/v1/prompts/${promptName}/active-version`, {
      method: "PUT",
      headers: this.headers(),
      body: JSON.stringify({ version }),
    })
    await raise(res)
  }

  // ── Usage ──────────────────────────────────────────────────────────────────

  /**
   * Fetch cost and token usage for the given date range.
   * Dates must be 'YYYY-MM-DD'.
   */
  async usageSummary(from: string, to: string): Promise<UsageSummary> {
    const res = await fetch(`${this.baseUrl}/v1/usage/summary?from=${from}&to=${to}`, { headers: this.headers() })
    await raise(res)
    return res.json()
  }

  // ── Internal ───────────────────────────────────────────────────────────────

  private headers(): Record<string, string> {
    const h: Record<string, string> = { "Content-Type": "application/json" }
    if (this.apiKey)      h["X-API-Key"]     = this.apiKey
    else if (this.token)  h["Authorization"] = `Bearer ${this.token}`
    return h
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

async function consumeSSE(
  res: Response,
  onToken?: (t: string) => void,
  onStatus?: (event: StatusEvent, data: string) => void,
): Promise<string> {
  const reader = res.body!.getReader()
  const decoder = new TextDecoder()
  const chunks: string[] = []
  let buffer = ""

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    // SSE blocks are separated by double newlines
    const blocks = buffer.split("\n\n")
    buffer = blocks.pop() ?? ""

    for (const block of blocks) {
      const lines = block.split("\n")
      const eventLine = lines.find((l) => l.startsWith("event:"))
      const dataLine  = lines.find((l) => l.startsWith("data:"))
      if (!dataLine) continue

      const event = eventLine?.slice("event:".length).trim()
      const data  = dataLine.slice("data:".length).trim()

      if (event === "token") {
        chunks.push(data)
        onToken?.(data)
      } else if (event === "error") {
        throw new GatewayError(`Gateway error: ${data}`)
      } else if (event === "attempting" || event === "fallback" || event === "done") {
        onStatus?.(event, data)
      }
    }
  }
  return chunks.join("")
}

/** Returns true if the JWT is expired or unreadable. */
function tokenExpired(token: string): boolean {
  try {
    const payloadB64 = token.split(".")[1]
    const padded = payloadB64 + "=".repeat((4 - (payloadB64.length % 4)) % 4)
    const payload = JSON.parse(Buffer.from(padded, "base64url").toString("utf8"))
    return Date.now() / 1000 >= payload.exp
  } catch {
    return true
  }
}

async function raise(res: Response): Promise<void> {
  if (res.ok) return
  const body = await res.text().catch(() => "")
  throw new GatewayError(`HTTP ${res.status}: ${body}`)
}

export class GatewayError extends Error {
  constructor(message: string) {
    super(message)
    this.name = "GatewayError"
  }
}
