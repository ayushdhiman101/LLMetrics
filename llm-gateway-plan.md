# LLM Gateway — Architecture Reference

> **Stack:** Java 21 · Spring Boot 3.3.4 · WebFlux · R2DBC · PostgreSQL 16 · React 18 · TypeScript · Vite

---

## System overview

```
Browser
  │
  │  HTTP (port 5173–517x)
  ▼
Vite Dev Server  ──proxy /v1/* and /auth/*──►  Spring Boot Gateway (port 8080)
                                                        │
                                                        │  R2DBC (reactive)
                                                        ▼
                                                  PostgreSQL 16
                                               (Docker, port 5432)
                                                        │
                                         ┌──────────────┼──────────────┐
                                         ▼              ▼              ▼
                                      OpenAI         Gemini       Anthropic
                                     (HTTPS)         (HTTPS)       (HTTPS)
```

---

## Request lifecycle — POST /v1/completions

```
1. HTTP request arrives at CompletionController
   └── Body: { message?, promptName?, version?, variables?, models[], strategy? }

2. JwtFilter (WebFilter)
   ├── Bearer token → validates JWT signature + expiry → injects User into reactor context
   └── X-API-Key → SHA-256 hashes key → looks up api_keys table → injects Tenant into context

3. GatewayService.process()
   ├── If promptName provided:
   │     └── PromptService fetches active (or named) version from prompt_versions
   │         └── Interpolates {{variables}} into final prompt string
   └── If raw message: uses message directly

4. Provider resolution loop (models[] in order)
   ├── Resolve provider by model prefix: gpt-* → OpenAI, gemini-* → Gemini, claude-* → Anthropic
   ├── Look up provider API key:
   │     1. user_provider_keys (JWT auth only) — decrypted with AES-256-GCM
   │     2. Server env var fallback (OPENAI_API_KEY / GEMINI_API_KEY / ANTHROPIC_API_KEY)
   ├── Call ProviderAdapter.stream() — returns Flux<String> of SSE token chunks
   └── On 429 / 5xx / timeout / open circuit breaker → try next model in list
       (only before first token emitted — after that, stream is committed)

5. SSE stream flows back to client
   └── event: attempting  (which model is being tried)
   └── event: token       (each text chunk)
   └── event: fallback    (if switching providers)
   └── event: done        (final model used)
   └── event: error       (unrecoverable failure)

6. doOnComplete() → async virtual thread
   └── Read token counts from provider's usage field in final SSE chunk
   └── Calculate cost: (input_tokens/1000 × input_price) + (output_tokens/1000 × output_price)
   └── Write UsageEvent to PostgreSQL (non-blocking to the response)
```

---

## Backend module map

```
com.llmgateway/
├── GatewayApplication.java          Entry point; disables Spring Security filter chain
│
├── auth/
│   ├── AuthController.java          POST /auth/register, /auth/login, /auth/logout
│   ├── AuthService.java             BCrypt hashing, user + tenant creation, JWT issuance
│   ├── JwtUtil.java                 JJWT 0.12.6 — sign (HS256) + validate tokens
│   ├── JwtFilter.java               WebFilter — extracts Bearer token, injects User into context
│   └── EncryptionService.java       AES-256-GCM — encrypt/decrypt provider keys
│
├── config/
│   ├── WebClientConfig.java         Reactive WebClient beans (one per provider)
│   ├── WebConfig.java               CORS config; camelCase JSON filter
│   └── EncryptionConfig.java        Initialises AES cipher from ENCRYPTION_KEY env var
│
├── domain/                          R2DBC entities (record classes)
│   ├── Tenant.java                  tenants table
│   ├── User.java                    users table
│   ├── UserProviderKey.java         user_provider_keys table
│   ├── ApiKey.java                  api_keys table (legacy X-API-Key path)
│   ├── Prompt.java                  prompts table
│   ├── PromptVersion.java           prompt_versions table
│   └── UsageEvent.java              usage_events table
│
├── gateway/
│   ├── CompletionController.java    POST /v1/completions — SSE endpoint
│   └── GatewayService.java          Orchestration: prompt resolution → routing → streaming
│
├── provider/
│   ├── ProviderAdapter.java         Interface: Flux<String> stream(ResolvedPromptRequest)
│   ├── openai/OpenAIAdapter.java    WebClient → OpenAI chat completions API
│   ├── gemini/GeminiAdapter.java    WebClient → Google Gemini generateContent API
│   ├── anthropic/AnthropicAdapter   WebClient → Anthropic messages API
│   └── keys/
│       ├── ProviderKeyController.java  GET/PUT/DELETE /v1/user/provider-keys
│       └── ProviderKeyService.java     Encrypt/decrypt + R2DBC CRUD
│
├── prompt/
│   ├── PromptController.java        Prompt + version CRUD endpoints
│   ├── PromptService.java           Version lookup, active-version management
│   └── PromptInterpolator.java      {{variable}} substitution
│
├── metrics/
│   └── UsageService.java            @Async over virtual threads — writes UsageEvent rows
│
├── repository/                      R2DBC repository interfaces (Spring Data)
└── dto/                             Request/response DTOs
```

---

## Database schema

### Tables and relationships

```
┌──────────────────────────────────┐
│             tenants              │
├──────────────────────────────────┤
│ PK  id               UUID        │
│     name             TEXT        │
│     monthly_token_budget  BIGINT │   ← added V2
│     webhook_url      TEXT        │   ← added V2
│     created_at       TIMESTAMPTZ│
└──┬───────────┬──────────┬────────┘
   │           │          │           │
  1:1         1:N        1:N         1:N
   │           │          │           │
   ▼           ▼          ▼           │
┌──────────────────┐  ┌────────────┐  ┌──────────────────┐        │
│      users       │  │  api_keys  │  │     prompts      │        │
├──────────────────┤  ├────────────┤  ├──────────────────┤        │
│ PK  id      UUID │  │ PK id      │  │ PK  id      UUID │        │
│ FK  tenant_id ◄──┼──┤ FK tenant_ │  │ FK  tenant_id    │        │
│     email   TEXT │  │    id      │  │     name    TEXT │        │
│     password_    │  │    key_hash│  │ UQ (tenant_id,   │        │
│     hash    TEXT │  │    created │  │      name)       │        │
│     created_at   │  └────────────┘  │     created_at   │        │
└────────┬─────────┘                  └────────┬─────────┘        │
         │ 1:N                                 │ 1:N              │
         ▼                                     ▼                  │
┌──────────────────────┐           ┌──────────────────────┐       │
│   user_provider_keys │           │    prompt_versions   │       │
├──────────────────────┤           ├──────────────────────┤       │
│ PK  id          UUID │           │ PK  id          UUID │       │
│ FK  user_id (CASCADE)│           │ FK  prompt_id        │       │
│     provider    TEXT │           │     version     TEXT │       │
│     encrypted_key    │           │     template    TEXT │       │
│         TEXT         │           │     description TEXT │       │
│     created_at       │           │     changelog   TEXT │       │
│     updated_at       │           │     is_active BOOLEAN│       │
│ UQ (user_id,         │           │     created_at       │       │
│      provider)       │           │ UQ (prompt_id,       │       │
└──────────────────────┘           │      version)        │       │
                                   └──────────────────────┘       │
                                                                   │
                                   ┌───────────────────────────────┘
                                   ▼
                        ┌──────────────────────────┐
                        │       usage_events       │
                        ├──────────────────────────┤
                        │ PK  id             UUID  │
                        │ FK  tenant_id            │
                        │ FK  prompt_id (nullable) │
                        │     prompt_version  TEXT │  ← snapshot, not FK
                        │     provider        TEXT │
                        │     model           TEXT │
                        │     input_tokens     INT │
                        │     output_tokens    INT │
                        │     cost_usd   NUMERIC   │
                        │     latency_ms       INT │
                        │     created_at           │
                        └──────────────────────────┘
```

### Key design decisions

- **`tenants` is the multi-tenancy root.** Every table except `user_provider_keys` has a `tenant_id` FK.
- **Users are 1:1 with tenants.** `tenant_id` on `users` is `UNIQUE`. Each registered user gets their own private tenant — prompt libraries and usage data are fully isolated.
- **`prompt_version` in `usage_events` is TEXT, not a FK.** It's a point-in-time snapshot so cost history stays accurate even if a version is deleted later.
- **`user_provider_keys` cascades on user delete.** Dropping a user automatically removes all their encrypted keys.
- **All PKs are UUID v4.** Random, non-enumerable, globally unique. No sequential integer PKs — prevents ID guessing on user-facing endpoints.

### Flyway migration history

| Version | Description |
|---|---|
| V1 | Initial schema: `tenants`, `api_keys`, `prompts`, `prompt_versions`, `usage_events` |
| V2 | Enable `pgcrypto`; add `monthly_token_budget` and `webhook_url` to `tenants` |
| V3 | Seed dev tenant, dev API key, and `hello` prompt |
| V4 | Add `users` and `user_provider_keys` tables |
| V5 | Drop unused `monthly_token_budget` and `webhook_url` columns; add performance indexes |

---

## Frontend architecture

```
frontend/src/
├── main.tsx                    React root
├── App.tsx                     Router + ProtectedApp shell (topbar + tab nav)
├── api.ts                      fetch wrappers: get / post / put / del
│                               └── put() checks Content-Type before calling res.json()
│                                   (PUT provider-keys returns 200 with empty body)
├── context/
│   └── AuthContext.tsx         JWT state: login / register / logout
│                               └── register() does NOT auto-login
│                                   (shows success banner, redirects to /login)
├── pages/
│   ├── Login.tsx               POST /auth/login → store JWT → navigate to /
│   ├── Register.tsx            POST /auth/register → success banner → navigate /login
│   ├── CostDashboard.tsx       GET /v1/usage/summary → StatCards + DailyChart + ProviderChart + ModelTable
│   ├── Prompts.tsx             Prompt CRUD + version management
│   ├── Playground.tsx          SSE streaming completions with model selector and fallback log
│   └── ApiKeySettings.tsx      GET/PUT/DELETE /v1/user/provider-keys per provider
└── components/
    ├── StatCard.tsx            KPI card (requests, cost, providers, models)
    ├── DailyChart.tsx          Recharts line chart — cost over time
    ├── ProviderChart.tsx       Recharts pie chart — provider split
    └── ModelTable.tsx          Cost breakdown by model
```

### Routing

All `/v1/*` and `/auth/*` requests from the browser go through Vite's dev proxy to `localhost:8080`. Both prefixes must be configured — `/auth/*` is commonly omitted by mistake.

Protected routes (`/`, `/login`, `/register`) are handled client-side by React Router. `ProtectedApp` redirects unauthenticated users to `/login` via `<Navigate>`.

### Auth state

The JWT is stored in `localStorage` under `llm-gateway-jwt`. On page load, `AuthContext` reads and decodes it (no server call). If the token is present and valid, the user is considered authenticated. The server validates the signature on every `/v1/*` request.

---

## Security model

| Concern | Implementation |
|---|---|
| Passwords | BCrypt (Spring Security's encoder, work factor 10) |
| Sessions | Stateless JWT, HS256, 24h TTL |
| Provider keys at rest | AES-256-GCM, key from `ENCRYPTION_KEY` env var |
| ID enumeration | UUID v4 PKs on all user-facing resources |
| Email normalisation | Trimmed and lowercased before storage and lookup |
| No auto-login after register | Token from registration response is discarded; explicit login required |
| Provider-key endpoints | JWT-only — `X-API-Key` returns 403 |
| Client token caching | `ensure_logged_in()` / `ensureLoggedIn()` decodes `exp` locally; no network call until expired |

---

## Client auth patterns

The gateway supports three auth patterns for programmatic callers. Client SDKs (`examples/python/client.py`, `examples/node/client.ts`) implement all three.

```
┌─────────────────────────────────────────────────────────────────────┐
│                      Which auth pattern?                            │
│                                                                     │
│   Need per-user provider keys?                                      │
│   ├── YES → use JWT (Options A or B)                                │
│   │         ├── Short-lived script / one-off task?                  │
│   │         │   └── Option A: login() once per process              │
│   │         │       Token lives in memory; gone when process exits  │
│   │         │                                                       │
│   │         └── Long-running service / cron / shared script?        │
│   │             └── Option B: ensure_logged_in() / ensureLoggedIn() │
│   │                 Saves JWT to .gateway_token                     │
│   │                 Reads exp claim locally on next start           │
│   │                 Only re-authenticates when expired (24h TTL)    │
│   │                                                                 │
│   └── NO  → use X-API-Key (Option C)                               │
│             Pass api_key= / { apiKey: } to constructor              │
│             Sends X-API-Key header; never expires                   │
│             Uses server-level env var keys only                     │
│             Cannot access /v1/user/provider-keys (403)              │
└─────────────────────────────────────────────────────────────────────┘
```

### JWT token flow (Options A & B)

```
Client                          Gateway                       PostgreSQL
  │                                │                               │
  │── POST /auth/login ───────────►│                               │
  │   {email, password}            │── SELECT * FROM users ───────►│
  │                                │◄─ BCrypt.verify(hash) ────────│
  │◄── {token, userId, ...} ───────│                               │
  │    (HS256, exp = now+24h)       │                               │
  │                                │                               │
  │── POST /v1/completions ────────►│                               │
  │   Authorization: Bearer <jwt>  │   (no DB call — self-contained)
  │                                │── JwtUtil.validate(token) ────►
  │                                │   verifies HS256 signature    │
  │                                │   checks exp claim            │
  │◄── SSE token stream ───────────│                               │
```

### X-API-Key flow (Option C)

```
Client                          Gateway                       PostgreSQL
  │                                │                               │
  │── POST /v1/completions ────────►│                               │
  │   X-API-Key: llm-gateway-...   │── SHA-256(key) ──────────────►│
  │                                │   SELECT FROM api_keys        │
  │                                │   WHERE key_hash = ?          │
  │                                │◄─ tenant row ─────────────────│
  │◄── SSE token stream ───────────│                               │
```

---

## Fallback and circuit breaker

Resilience4j wraps each provider adapter. The circuit breaker opens after consecutive failures and routes traffic to the next entry in the `models` array automatically.

Fallback triggers on: `429 Too Many Requests`, `5xx` server error, connection timeout.  
Fallback does **not** trigger on: `400 Bad Request`, `401 Unauthorized` (including Anthropic's billing-empty 400).

The fallback is pre-stream only. Once the first token has been sent to the client, the connection is committed to that provider.

---

## Pricing config

In `src/main/resources/application.yml`:

```yaml
pricing:
  models:
    gpt-4o:
      input-per-1k: 0.0025
      output-per-1k: 0.0100
    gpt-4o-mini:
      input-per-1k: 0.000150
      output-per-1k: 0.000600
    "[gemini-2.5-flash]":          # dot in name requires bracket notation
      input-per-1k: 0.00015
      output-per-1k: 0.00060
    claude-sonnet-4-6:
      input-per-1k: 0.003
      output-per-1k: 0.015
```

Adding a new model is config-only — no code change required.

---

## Known bugs fixed

| Bug | Root cause | Fix |
|---|---|---|
| DELETE provider key returns 204 but row stays in DB | Spring Data R2DBC derived `deleteBy...` methods return an empty `Mono<Void>` without executing SQL unless annotated with `@Modifying` + `@Query` | Added explicit `@Modifying @Query("DELETE FROM user_provider_keys WHERE ...")` to `UserProviderKeyRepository` |
| All navigation tabs blank after login | Pages referenced an undeclared `apiKey` variable left over from an older design; React threw a `ReferenceError` that silently caught and rendered nothing | Removed the stale guard and dependency from `CostDashboard`, `Playground`, and `Prompts` |
| PUT provider-keys throws "Unexpected end of JSON input" | `PUT /v1/user/provider-keys/{provider}` returns 200 with empty body; frontend `put<T>` called `res.json()` unconditionally | `put<T>` now checks `Content-Type` header before parsing; returns `undefined` on empty body |
| Registration auto-logged user in, caused stuck screen | `register()` in `AuthContext` stored the JWT and set auth state, but the Register page had no navigation handler | `register()` now discards the token; Register page shows 2s success banner then redirects to `/login` |
| Vite proxy missing `/auth/*` | `vite.config.ts` only proxied `/v1/*`; auth endpoints returned 404 from Vite | Added `/auth` entry to the proxy config |

---

## Production deployment architecture

```
Browser
  │  HTTPS
  ▼
Vercel  (React static bundle, built from frontend/)
  │  fetch(VITE_API_URL + "/v1/...")   ← env var set at Vercel build time
  │  fetch(VITE_API_URL + "/auth/...")
  ▼
Render  (Spring Boot Docker container, built from backend/Dockerfile)
  │  env: CORS_ALLOWED_ORIGINS=https://your-app.vercel.app
  │  env: R2DBC_URL / JDBC_URL / FLYWAY_URL → Neon
  │  env: JWT_SECRET, ENCRYPTION_KEY (strong random values)
  │  R2DBC (reactive) + JDBC (Flyway only)
  ▼
Neon  (serverless PostgreSQL, free tier)
```

### Frontend production wiring

In dev, Vite's proxy (`vite.config.ts`) forwards `/v1/*` and `/auth/*` to `localhost:8080` — no `VITE_API_URL` needed.

In production, `VITE_API_URL` is injected at Vite build time as `import.meta.env.VITE_API_URL`. Every `fetch` in `api.ts` and `AuthContext.tsx` is prefixed with this value. If the env var is absent, the prefix is empty and relative paths are used (dev behaviour preserved).

### Backend production wiring

All sensitive values come from environment variables with safe local defaults:

| Env var | Local default | Production value |
|---|---|---|
| `R2DBC_URL` | `r2dbc:postgresql://localhost:5432/llmgateway` | Neon R2DBC URL with `sslMode=REQUIRE` |
| `JDBC_URL` | `jdbc:postgresql://localhost:5432/llmgateway?TimeZone=UTC` | Neon JDBC URL with `sslmode=require` |
| `FLYWAY_URL` | same as JDBC_URL | Neon JDBC URL with timezone option |
| `DB_USERNAME` | `postgres` | Neon username |
| `DB_PASSWORD` | `postgres` | Neon password |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:*` | `https://your-app.vercel.app` |
| `JWT_SECRET` | dev placeholder | strong random string (≥32 chars) |
| `ENCRYPTION_KEY` | dev placeholder | 64 hex chars (32 random bytes) |
