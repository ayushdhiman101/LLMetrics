export interface UsageSummaryResponse {
  from: string
  to: string
  totalRequests: number
  totalCostUsd: number
  byProvider: ProviderBreakdown[]
  byModel: ModelBreakdown[]
  byDay: DailyBreakdown[]
}

export interface ProviderBreakdown {
  provider: string
  requests: number
  inputTokens: number
  outputTokens: number
  costUsd: number
}

export interface ModelBreakdown {
  model: string
  provider: string
  requests: number
  inputTokens: number
  outputTokens: number
  costUsd: number
}

export interface DailyBreakdown {
  day: string
  requests: number
  inputTokens: number
  outputTokens: number
  costUsd: number
}

export interface PromptResponse {
  id: string
  tenantId: string
  name: string
  createdAt: string
}

export interface PromptVersionResponse {
  id: string
  promptId: string
  version: string
  template: string
  description: string | null
  changelog: string | null
  isActive: boolean
  createdAt: string
}

// In dev, VITE_API_URL is unset and Vite's proxy handles /v1/* and /auth/*.
// In production (Vercel), set VITE_API_URL=https://your-backend.onrender.com
export const API_BASE = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '')

function authHeaders(): HeadersInit {
  const token = localStorage.getItem('llm-gateway-jwt')
  const h: Record<string, string> = { 'Content-Type': 'application/json' }
  if (token) h['Authorization'] = `Bearer ${token}`
  return h
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, { headers: authHeaders() })
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
  return res.json()
}

async function post<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, { method: 'POST', headers: authHeaders(), body: JSON.stringify(body) })
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
  return res.json()
}

async function put<T>(path: string, body: unknown): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, { method: 'PUT', headers: authHeaders(), body: JSON.stringify(body) })
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
  const ct = res.headers.get('content-type') ?? ''
  if (!ct.includes('application/json')) return undefined as T
  return res.json()
}

async function del(path: string): Promise<void> {
  const res = await fetch(`${API_BASE}${path}`, { method: 'DELETE', headers: authHeaders() })
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
}

export interface ProviderKeyResponse {
  provider: string
  configured: boolean
  updatedAt: string
}

export interface CompletionRequest {
  promptName?: string
  version?: string
  variables?: Record<string, string>
  message?: string
  models: string[]
  strategy?: string
}

export type StatusEvent = { type: 'attempting' | 'fallback' | 'done'; data: string }

export async function streamCompletion(
  req: CompletionRequest,
  onToken: (token: string) => void,
  onStatus: (e: StatusEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  const res = await fetch(`${API_BASE}/v1/completions`, {
    method: 'POST',
    headers: authHeaders(),
    body: JSON.stringify(req),
    signal,
  })
  if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)

  const reader = res.body!.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() ?? ''
    for (const block of blocks) {
      const lines = block.split('\n')
      const eventLine = lines.find(l => l.startsWith('event:'))
      const dataLine = lines.find(l => l.startsWith('data:'))
      if (!dataLine) continue
      const data = dataLine.replace(/^data:\s*/, '')
      const event = eventLine?.replace(/^event:\s*/, '')
      if (event === 'error') throw new Error(data)
      if (event === 'token') onToken(data)
      if (event === 'attempting') onStatus({ type: 'attempting', data })
      if (event === 'fallback') onStatus({ type: 'fallback', data })
      if (event === 'done') onStatus({ type: 'done', data })
    }
  }
}

export const api = {
  fetchSummary: (from: string, to: string) =>
    get<UsageSummaryResponse>(`/v1/usage/summary?from=${from}&to=${to}`),

  listPrompts: () =>
    get<PromptResponse[]>('/v1/prompts'),

  listVersions: (name: string) =>
    get<PromptVersionResponse[]>(`/v1/prompts/${name}/versions`),

  createPrompt: (name: string) =>
    post<PromptResponse>('/v1/prompts', { name }),

  createVersion: (name: string, body: { version: string; template: string; description?: string; isActive?: boolean }) =>
    post<PromptVersionResponse>(`/v1/prompts/${name}/versions`, body),

  setActiveVersion: (name: string, version: string) =>
    put<PromptVersionResponse>(`/v1/prompts/${name}/active-version`, { version }),

  listProviderKeys: () =>
    get<ProviderKeyResponse[]>('/v1/user/provider-keys'),

  upsertProviderKey: (provider: string, apiKey: string) =>
    put<void>(`/v1/user/provider-keys/${provider}`, { apiKey }),

  deleteProviderKey: (provider: string) =>
    del(`/v1/user/provider-keys/${provider}`),
}
