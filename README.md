# LLM Gateway

Reactive Java gateway for LLM API calls with cost observability, prompt versioning, multi-provider fallback, and a React dashboard. Built on Spring Boot 3 + WebFlux + R2DBC + PostgreSQL.

## Features

| Feature | Description |
|---|---|
| Cost observability | Per-request token counts and USD cost persisted to PostgreSQL; dashboard with daily/provider/model breakdowns |
| Prompt versioning | Versioned templates with `{{variable}}` interpolation and active-version rollback |
| Multi-provider fallback | Automatic failover across OpenAI, Gemini, Anthropic via Resilience4j circuit breakers |
| User management | Registration, login/logout via JWT; per-user provider API keys encrypted at rest (AES-256-GCM) |
| React dashboard | Cost charts, prompt manager, completion playground, API key settings |

---

## Quickstart

### 1. Start Postgres

```bash
docker compose up -d postgres
```

### 2. Configure environment

Copy `.env.example` → `backend/.env` and fill in:

```env
OPENAI_API_KEY=sk-...
GEMINI_API_KEY=AIza...
ANTHROPIC_API_KEY=sk-ant-...

# JWT signing secret — at least 32 characters
JWT_SECRET=change-me-in-production-must-be-at-least-32-chars

# AES-256 key for encrypting stored provider keys — exactly 64 hex chars
ENCRYPTION_KEY=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

At least one provider key is required. Users can also supply their own via the dashboard, which takes priority over the server defaults.

### 3. Start the backend

```bash
cd backend
mvn spring-boot:run
```

Listens on `http://localhost:8080`. Flyway runs all migrations on startup automatically.

### 4. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Opens on `http://localhost:5173` (or the next available port — check terminal output).

### 5. Register and log in

Open the frontend URL in your browser. Click **Create account**, fill in email and password (min 8 characters). After registration you'll see a confirmation banner and be redirected to the login page automatically. Log in with the same credentials.

### 6. Add your provider keys

Go to the **API Keys** tab. Paste your OpenAI, Gemini, and/or Anthropic keys. Keys are encrypted with AES-256-GCM before being stored — the plaintext never persists.

### 7. Send a completion

Go to **Playground**, type a message, select one or more models, and click **Run**. Tokens stream in real time. Select multiple models to enable the fallback chain.

### 8. Check cost

Go to **Cost** to see per-provider and per-model spend, request volume, and a daily cost chart.

---

## API

### Authentication

All `/v1/*` endpoints accept one of:

- `Authorization: Bearer <jwt>` — JWT from `/auth/login` or `/auth/register`
- `X-API-Key: <key>` — static key (dev seed: `llm-gateway-dev-key`)

Auth endpoints (`/auth/*`) are open — no header required.

Provider-key endpoints (`/v1/user/provider-keys`) require JWT — they are user-specific and have no tenant-level equivalent.

#### Client SDK auth patterns

Three options depending on your use case:

| Option | How | When to use |
|---|---|---|
| **JWT — in memory** | `login(email, password)` once per process | Short-lived scripts, one-shot tasks |
| **JWT — cached to disk** | `ensure_logged_in()` / `ensureLoggedIn()` | Long-running services, cron jobs — avoids re-login while token is valid (24h TTL) |
| **X-API-Key** | Pass `api_key=` / `{ apiKey: }` to constructor | Server-to-server calls where no user context is needed; uses server-level provider keys |

Token caching (`ensure_logged_in`) reads/writes a `.gateway_token` file. It decodes the JWT's `exp` claim locally (no network call) and only re-authenticates when the token is expired. Add `.gateway_token` to `.gitignore`.

### Endpoints

| Method | Path | Auth | Description |
|---|---|---|---|
| `POST` | `/auth/register` | none | `{email, password}` → `{token, userId, tenantId, email}` |
| `POST` | `/auth/login` | none | `{email, password}` → `{token, userId, tenantId, email}` |
| `POST` | `/auth/logout` | none | `200` (client discards token; JWT is stateless) |
| `GET` | `/v1/user/provider-keys` | JWT | List configured providers (configured flag only — never returns plaintext) |
| `PUT` | `/v1/user/provider-keys/{provider}` | JWT | `{apiKey}` — save or replace key for `openai`, `gemini`, or `anthropic` |
| `DELETE` | `/v1/user/provider-keys/{provider}` | JWT | Remove stored key (gateway falls back to server env var) |
| `POST` | `/v1/completions` | either | `{message?, promptName?, version?, variables?, models, strategy?}` |
| `POST` | `/v1/prompts` | either | `{name}` — create a prompt |
| `GET` | `/v1/prompts/{name}/versions` | either | List all versions for a prompt |
| `POST` | `/v1/prompts/{name}/versions` | either | `{version, template, description?, isActive?}` |
| `PUT` | `/v1/prompts/{name}/active-version` | either | `{version}` — set active version |
| `GET` | `/v1/usage/summary` | either | `?from=YYYY-MM-DD&to=YYYY-MM-DD` |

---

## Routing & fallback

`models` is an ordered fallback chain. Provider is resolved by model name prefix:

| Prefix | Provider |
|---|---|
| `gpt-*` | OpenAI |
| `gemini-*` | Google Gemini |
| `claude-*` | Anthropic |

The chain tries each entry in order. A `429`, `5xx`, timeout, or open circuit breaker causes a fallthrough to the next entry — but only **before any tokens are emitted**. Once the first token reaches the client, the stream is committed.

```bash
# Try GPT-4o first, fall back to Gemini
curl -N -X POST http://localhost:8080/v1/completions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello","models":["gpt-4o-mini","gemini-2.5-flash"]}'
```

---

## Provider key resolution

For each request, keys are resolved in this order:

1. **User's own key** — from `user_provider_keys`, decrypted at request time (JWT auth only)
2. **Server default** — from environment variables (`OPENAI_API_KEY`, `GEMINI_API_KEY`, `ANTHROPIC_API_KEY`)

If neither is configured for a provider, that provider returns 401/403, which triggers the fallback chain.

---

## Pricing

Config-driven in `src/main/resources/application.yml` under `pricing.models.<model>`. Adding a new model requires only a config entry — no code change.

Model names containing dots (e.g. `gemini-2.5-flash`) must use bracket notation in YAML:

```yaml
pricing:
  models:
    "[gemini-2.5-flash]":
      input-per-1k: 0.00015
      output-per-1k: 0.0006
```

---

## Tech stack

| Layer | Technology |
|---|---|
| Gateway API | Java 21, Spring Boot 3.3.4, Spring WebFlux (Netty) |
| Database access | R2DBC (reactive, request path) + JDBC (Flyway migrations only) |
| Auth | JJWT 0.12.6 (JWT), BCrypt (passwords), AES-256-GCM (provider key encryption) |
| Fallback / circuit breakers | Resilience4j 2.2.0 |
| Async metrics | Spring `@Async` over Java 21 virtual threads |
| Migrations | Flyway 10 |
| Frontend | React 18, TypeScript, Vite 5, React Router v7, Recharts |

---

## Database schema

```
tenants ─────────────────────────────────────┐
   │ 1:1                                     │
users ──► user_provider_keys (cascade delete) │ 1:N to all
   │                                         │
api_keys ◄───────────────────────────────────┤
prompts  ◄───────────────────────────────────┤
   │ 1:N                                     │
prompt_versions                              │
                                             │
usage_events ◄───────────────────────────────┘
   └── FK → prompts (nullable — set only for prompt-mode requests)
```

Each registered user gets their own `tenant` row (1:1). All data — prompts, usage, API keys — is scoped to that tenant.

---

## Free deployment

Deploy free with no credit card: **Neon** (PostgreSQL) + **Render** (Spring Boot) + **Vercel** (React).

| Component | Platform | Notes |
|---|---|---|
| Database | [Neon](https://neon.tech) | Free forever, serverless PostgreSQL |
| Backend | [Render](https://render.com) | Free Docker deploys; sleeps after 15 min idle |
| Frontend | [Vercel](https://vercel.com) | Free forever, instant static deploys |

Three environment variables control production wiring:

| Variable | Where to set | Value |
|---|---|---|
| `R2DBC_URL` | Render | Neon R2DBC connection string |
| `CORS_ALLOWED_ORIGINS` | Render | `https://your-app.vercel.app` |
| `VITE_API_URL` | Vercel | `https://your-backend.onrender.com` |

See `RUNBOOK.md → Free deployment` for the full step-by-step guide.

---

## License

MIT
