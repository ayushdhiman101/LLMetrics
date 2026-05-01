# LLM Gateway — Client Examples

Two drop-in clients that show how to call the gateway from your own codebase.

```
examples/
├── python/
│   ├── client.py        ← import this into your project
│   ├── demo.py          ← runnable end-to-end walkthrough
│   └── requirements.txt
└── node/
    ├── client.ts        ← import this into your project
    ├── demo.ts          ← runnable end-to-end walkthrough
    └── package.json
```

---

## Python

```bash
cd examples/python
pip install -r requirements.txt
python demo.py
```

### Usage in your code

```python
from client import GatewayClient

# Option A — login once per process (JWT in memory, valid 24h)
gw = GatewayClient("http://localhost:8080")
gw.login("alice@example.com", "secret123")

# Option B — token caching across process restarts (reads/writes .gateway_token)
gw = GatewayClient("http://localhost:8080")
gw.ensure_logged_in("alice@example.com", "secret123")  # only re-logins when expired

# Option C — API key (no login needed, uses server-level provider keys)
gw = GatewayClient("http://localhost:8080", api_key="llm-gateway-dev-key")

# One-liner — returns the full response string
answer = gw.complete(
    message="Explain transformers in one sentence.",
    models=["gemini-2.5-flash"],
)
print(answer)

# Stream tokens as they arrive
gw.complete(
    message="Write a haiku about databases.",
    models=["gemini-2.5-flash", "gpt-4o-mini"],   # falls back if first fails
    on_token=lambda t: print(t, end="", flush=True),
    on_status=lambda event, data: print(f"\n[{event}] {data}"),
)

# Use a saved prompt template
gw.complete(
    prompt_name="explain",
    variables={"concept": "backpropagation", "audience": "a chef"},
    models=["gemini-2.5-flash"],
    on_token=lambda t: print(t, end="", flush=True),
)

# Check your spend
summary = gw.usage_summary("2025-01-01", "2025-12-31")
print(f"Total cost: ${summary['totalCostUsd']:.6f}")
```

---

## Node.js / TypeScript

```bash
cd examples/node
npm install
npm run demo
```

Requires Node 20+ (uses native `fetch` and `ReadableStream`).

### Usage in your code

```typescript
import { GatewayClient } from "./client.ts"

// Option A — login once per process (JWT in memory, valid 24h)
const gw = new GatewayClient("http://localhost:8080")
await gw.login("alice@example.com", "secret123")

// Option B — token caching across process restarts (reads/writes .gateway_token)
const gw = new GatewayClient("http://localhost:8080")
await gw.ensureLoggedIn("alice@example.com", "secret123")  // only re-logins when expired

// Option C — API key (no login needed, uses server-level provider keys)
const gw = new GatewayClient("http://localhost:8080", { apiKey: "llm-gateway-dev-key" })

// One-liner
const answer = await gw.complete({
  message: "Explain transformers in one sentence.",
  models: ["gemini-2.5-flash"],
})
console.log(answer)

// Stream tokens
await gw.complete({
  message: "Write a haiku about databases.",
  models: ["gemini-2.5-flash", "gpt-4o-mini"],
  onToken: (t) => process.stdout.write(t),
  onStatus: (event, data) => console.log(`\n[${event}] ${data}`),
})

// Prompt template
await gw.complete({
  promptName: "explain",
  variables: { concept: "backpropagation", audience: "a chef" },
  models: ["gemini-2.5-flash"],
  onToken: (t) => process.stdout.write(t),
})

// Cost summary
const summary = await gw.usageSummary("2025-01-01", "2025-12-31")
console.log(`Total cost: $${summary.totalCostUsd.toFixed(6)}`)
```

---

## What the demo covers

| Step | What it shows |
|---|---|
| 1. Register | Create a new account (idempotent — skips if already exists) |
| 2. Login | Authenticate and receive a JWT |
| 3. Provider keys | List which providers are configured for your account |
| 4. Raw completion | Stream a plain text message; see which model served it |
| 5. Prompt template | Create a versioned template with `{{variables}}` and call it |
| 6. Versioning | Add v2, use it, then roll back to v1 with one call |
| 7. Fallback chain | Pass multiple models; watch the gateway fall through on failure |
| 8. Usage summary | Pull cost and token counts for the last 7 days |

---

## Model IDs

| Model | Provider |
|---|---|
| `gpt-4o` | OpenAI |
| `gpt-4o-mini` | OpenAI |
| `gemini-2.5-flash` | Google |
| `gemini-flash-latest` | Google |
| `claude-sonnet-4-6` | Anthropic |
| `claude-haiku-4-5-20251001` | Anthropic |
| `claude-opus-4-7` | Anthropic |

---

## Environment variables

| Variable | Default | Description |
|---|---|---|
| `GATEWAY_URL` | `http://localhost:8080` | Gateway base URL |
| `GATEWAY_EMAIL` | `demo@example.com` | Demo account email |
| `GATEWAY_PASSWORD` | `demo-password-123` | Demo account password |

```bash
GATEWAY_URL=https://your-gateway.com \
GATEWAY_EMAIL=you@company.com \
GATEWAY_PASSWORD=yourpassword \
python demo.py
```
