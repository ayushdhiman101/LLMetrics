# Runbook — LLM Gateway

Operational reference for running, testing, and debugging the gateway locally.

---

## Start everything

### 1. Postgres (Docker)

```bash
docker compose up -d postgres
```

Verify it's healthy:

```bash
docker ps
# llmgateway-postgres should show (healthy)
```

### 2. Backend (Spring Boot)

```bash
cd backend
mvn spring-boot:run
```

Or run the JAR directly (must pass timezone flag to avoid Flyway crash):

```bash
java -Duser.timezone=UTC -jar backend/target/llm-gateway-0.1.0-SNAPSHOT.jar
```

Watch for `Started GatewayApplication` in the logs. Flyway migrations run automatically on startup.

Health check:

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}
```

### 3. Frontend (Vite dev server)

```bash
cd frontend
npm install      # first time only
npm run dev
```

Vite picks the first available port starting at 5173. If 5173–5175 are in use it will move up — check the terminal output for the actual URL:

```
VITE v5.4.21  ready in 640 ms
➜  Local: http://localhost:5176/
```

The Vite dev server proxies two path prefixes to the backend:

| Prefix | Target |
|---|---|
| `/v1/*` | `http://localhost:8080` |
| `/auth/*` | `http://localhost:8080` |

Both must be proxied. If you see auth requests failing (404 from Vite), check `frontend/vite.config.ts`.

---

## First-time setup

```bash
# 1. Start Postgres
docker compose up -d postgres

# 2. Start backend (Flyway creates all tables)
cd backend && mvn spring-boot:run

# 3. Start frontend
cd frontend && npm run dev

# 4. Open the frontend URL in your browser
#    Register an account → confirmation banner appears → redirected to login
#    Log in with the same credentials

# 5. Go to API Keys tab → add at least one provider key
#    Or rely on the server env vars in backend/.env
```

---

## Auth flow (browser)

**Registration:**

1. POST `/auth/register` with `{email, password}`
2. Backend creates a `users` row + a dedicated `tenants` row (1:1) and returns a JWT
3. Frontend discards the token (no auto-login), shows a green success banner for 2 seconds, then redirects to `/login`
4. User logs in explicitly

**Login:**

1. POST `/auth/login` with `{email, password}`
2. Backend validates BCrypt hash, returns `{token, userId, tenantId, email}`
3. Frontend stores the JWT in `localStorage` under `llm-gateway-jwt`
4. All subsequent API calls send `Authorization: Bearer <token>`
5. Token TTL is 24 hours

**Logout:**

Frontend removes the token from `localStorage` and fires `POST /auth/logout` (best-effort). The JWT is stateless so no server-side session is invalidated.

---

## Auth flow (client SDKs)

Two drop-in clients live in `examples/python/` and `examples/node/`. Each supports three auth patterns:

**Option A — JWT, in memory (re-login each process start):**

```python
# Python
gw = GatewayClient("http://localhost:8080")
gw.login("alice@example.com", "secret123")
```
```typescript
// TypeScript
const gw = new GatewayClient("http://localhost:8080")
await gw.login("alice@example.com", "secret123")
```

**Option B — JWT, cached to disk (only re-logins when token expires):**

```python
# Python — reads/writes .gateway_token; skips login if token is still valid
gw = GatewayClient("http://localhost:8080")
gw.ensure_logged_in("alice@example.com", "secret123")
```
```typescript
// TypeScript — same behaviour
const gw = new GatewayClient("http://localhost:8080")
await gw.ensureLoggedIn("alice@example.com", "secret123")
```

The token file is decoded locally (no network call) to check the `exp` claim. Token TTL is 24 hours. Add `.gateway_token` to `.gitignore`.

**Option C — X-API-Key (no login, server-level provider keys):**

```python
gw = GatewayClient("http://localhost:8080", api_key="llm-gateway-dev-key")
```
```typescript
const gw = new GatewayClient("http://localhost:8080", { apiKey: "llm-gateway-dev-key" })
```

Note: X-API-Key auth cannot access `/v1/user/provider-keys` (403). It uses only the server's environment variable keys.

---

## Auth flow (curl)

```bash
# Register
curl -s -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"secret123"}'
# → {"token":"eyJ...","userId":"...","tenantId":"...","email":"alice@example.com"}

# Login — capture token
export TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"secret123"}' | jq -r .token)

# Logout
curl -X POST http://localhost:8080/auth/logout \
  -H "Authorization: Bearer $TOKEN"
```

---

## Manage provider keys

```bash
# List (shows which providers are configured — never returns the plaintext key)
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/v1/user/provider-keys | jq

# Save / replace a key
curl -X PUT http://localhost:8080/v1/user/provider-keys/openai \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"apiKey":"sk-..."}'

curl -X PUT http://localhost:8080/v1/user/provider-keys/gemini \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"apiKey":"AIza..."}'

curl -X PUT http://localhost:8080/v1/user/provider-keys/anthropic \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"apiKey":"sk-ant-..."}'

# Remove a key (gateway falls back to server env var)
curl -X DELETE http://localhost:8080/v1/user/provider-keys/openai \
  -H "Authorization: Bearer $TOKEN"
```

Keys are encrypted with AES-256-GCM before storage. The `PUT` endpoint returns `200 OK` with an empty body.

---

## Stream a completion

### Raw message

```bash
curl -N -X POST http://localhost:8080/v1/completions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Explain reactive streams in one sentence.",
    "models": ["gemini-2.5-flash"]
  }'
```

SSE event stream — you'll see `event: token` frames followed by `event: done`.

### Using a prompt template

```bash
curl -N -X POST http://localhost:8080/v1/completions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "promptName": "hello",
    "variables": {"name": "Alice"},
    "models": ["gemini-2.5-flash"]
  }'
```

### Multi-model fallback chain

```bash
curl -N -X POST http://localhost:8080/v1/completions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello","models":["gpt-4o-mini","gemini-2.5-flash","claude-haiku-4-5-20251001"]}'
```

The gateway tries each model left-to-right. A `429`, `5xx`, timeout, or open circuit breaker moves to the next. The `usage_events` row will show whichever provider actually responded.

### Supported model IDs

| Model ID | Provider |
|---|---|
| `gpt-4o` | OpenAI |
| `gpt-4o-mini` | OpenAI |
| `gemini-flash-latest` | Google |
| `gemini-2.5-flash` | Google |
| `claude-haiku-4-5-20251001` | Anthropic |
| `claude-sonnet-4-6` | Anthropic |
| `claude-opus-4-7` | Anthropic |

---

## Legacy X-API-Key path

The seeded dev tenant still works via `X-API-Key`. This path uses server env var keys only — no per-user key lookup.

```bash
curl -N -X POST http://localhost:8080/v1/completions \
  -H "X-API-Key: llm-gateway-dev-key" \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello","models":["gemini-2.5-flash"]}'
```

Note: `GET/PUT/DELETE /v1/user/provider-keys` always return `403` for X-API-Key requests — those endpoints are JWT-only.

---

## Prompt CRUD

```bash
# Create a prompt
curl -X POST http://localhost:8080/v1/prompts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"summarize"}'

# Add a version
curl -X POST http://localhost:8080/v1/prompts/summarize/versions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"version":"v1","template":"Summarize in one sentence: {{content}}","isActive":true}'

# List versions
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/v1/prompts/summarize/versions | jq

# Add a new version and make it active
curl -X POST http://localhost:8080/v1/prompts/summarize/versions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"version":"v2","template":"TL;DR: {{content}}","isActive":true}'

# Roll back to v1
curl -X PUT http://localhost:8080/v1/prompts/summarize/active-version \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"version":"v1"}'
```

---

## Cost / usage summary

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/v1/usage/summary?from=2025-01-01&to=2025-12-31" | jq
```

Or query the database directly:

```bash
docker exec -i llmgateway-postgres psql -U postgres -d llmgateway -c \
  "SELECT provider, model, input_tokens, output_tokens, cost_usd, latency_ms, created_at
   FROM usage_events ORDER BY created_at DESC LIMIT 10;"
```

---

## Verify encryption in the database

```bash
docker exec -i llmgateway-postgres psql -U postgres -d llmgateway <<'SQL'
-- User joined to their auto-created tenant
SELECT u.email, t.name AS tenant_name, u.created_at
FROM users u JOIN tenants t ON u.tenant_id = t.id;

-- Provider keys — should show opaque ciphertext, never raw key values
SELECT user_id, provider, LEFT(encrypted_key, 40) || '...' AS encrypted_preview, updated_at
FROM user_provider_keys;
SQL
```

---

## Seeded state (V3 migration)

Flyway pre-seeds a dev tenant on every fresh database:

| Item | Value |
|---|---|
| Tenant ID | `00000000-0000-0000-0000-000000000001` |
| Tenant name | `dev-tenant` |
| API key (plaintext) | `llm-gateway-dev-key` |
| Prompt | `hello` — version `v1`, template `Say hello to {{name}} in one sentence.` |

This seed is idempotent (`ON CONFLICT DO NOTHING`) — it won't re-insert if you restart without wiping the volume.

---

## Stop everything

```bash
# Stop the backend (find the PID)
# On Windows:
powershell "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -like '*llm-gateway*' } | Select ProcessId"
powershell "Stop-Process -Id <PID> -Force"

# Kill the Vite dev server
pkill -f vite     # Linux/Mac
# Windows: close the terminal or use Task Manager

# Stop Postgres (keeps data)
docker compose down

# Stop Postgres and wipe all data (next start re-runs all migrations)
docker compose down -v
```

---

## Clean slate

```bash
docker compose down -v
docker compose up -d postgres
cd backend && mvn spring-boot:run
# Register a new user — fresh database, no prior usage events
```

---

## Gotchas

1. **JVM timezone.** Running `java -jar` without `-Duser.timezone=UTC` crashes Flyway because PostgreSQL 16 rejects the `Asia/Calcutta` timezone. `mvn spring-boot:run` passes the flag automatically via `pom.xml`. Always prefer Maven for local dev.

2. **Model names with dots in `application.yml`.** YAML treats dots as nested keys. Model IDs like `gemini-2.5-flash` must use bracket notation:
   ```yaml
   pricing:
     models:
       "[gemini-2.5-flash]":
         input-per-1k: 0.00015
         output-per-1k: 0.0006
   ```
   Without brackets, the config binder silently fails and `cost_usd` stays zero.

3. **Vite proxy must cover `/auth/*`.** The dev server only proxies what's in `vite.config.ts`. Both `/v1` and `/auth` must be listed. If you see registration or login returning 404, this is the cause.

4. **PUT provider-keys returns empty body.** `PUT /v1/user/provider-keys/{provider}` returns `200 OK` with no response body. Any client code that calls `res.json()` on the response will throw "Unexpected end of JSON input". The frontend `put<T>` helper checks `Content-Type` before parsing.

5. **OpenAI quota.** `insufficient_quota` from upstream surfaces as `event: error` in the SSE stream, not a 5xx. It won't trigger the fallback chain. Check your OpenAI credit balance.

6. **Anthropic billing returns 400, not 429.** When an Anthropic account has no credit, the API returns `400 Bad Request` — the same code as a malformed request. The fallback predicate (429/5xx only) won't fall through, so a credit-empty Anthropic entry in the chain hard-fails rather than falling back. Top up at console.anthropic.com or remove it from the chain.

7. **Anthropic model ID format.** `claude-haiku-4-5` is not valid — the full date suffix is required: `claude-haiku-4-5-20251001`. `claude-sonnet-4-6` and `claude-opus-4-7` work without a date suffix.

8. **JWT_SECRET strength.** The default dev value is fine locally. In production, set `JWT_SECRET` to a cryptographically random string of at least 32 characters.

9. **ENCRYPTION_KEY format.** Must be exactly 64 hex characters (32 bytes). Rotating this key invalidates all stored provider keys — they cannot be decrypted with a different key. Never change it in a running production environment without first exporting and re-encrypting all keys.

10. **Provider key endpoints are JWT-only.** `GET/PUT/DELETE /v1/user/provider-keys` return `403 Forbidden` if the request uses `X-API-Key` instead of `Bearer`. These endpoints are user-specific; there is no tenant-level equivalent.

11. **`DELETE /v1/user/provider-keys/{provider}` requires `@Modifying` + `@Query`.** Spring Data R2DBC derived `deleteBy...` methods do not execute a DELETE SQL statement without an explicit `@Modifying @Query` annotation. Without it the Mono completes empty (success returned to client) but no row is removed. Fixed in `UserProviderKeyRepository`.

12. **Python demo on Windows — no Unicode outside ASCII.** Windows cp1252 encoding rejects characters like `✓`, `✗`, `→`, `↷`. All demo output uses ASCII only. Use `python -u demo.py` (unbuffered) to see live output; without `-u` stdout is buffered and may appear out of order when piped.

---

## Free deployment (Neon + Render + Vercel)

Deploy all three components for free with no credit card required (Render free tier spins down after 15 min inactivity; first request after sleep takes ~30s to wake up).

### Step 1 — Database: Neon

1. Sign up at [neon.tech](https://neon.tech) — free forever tier.
2. Create a project → create a database named `llmgateway`.
3. From the **Connection Details** panel, copy:
   - **Connection string** (JDBC format): `jdbc:postgresql://ep-xxx.region.aws.neon.tech/llmgateway?sslmode=require`
   - The same host/user/password for the R2DBC URL below.

You will need three env vars for Render:

```
DB_USERNAME=<from Neon>
DB_PASSWORD=<from Neon>
JDBC_URL=jdbc:postgresql://<host>/llmgateway?sslmode=require&TimeZone=UTC
FLYWAY_URL=jdbc:postgresql://<host>/llmgateway?sslmode=require&options=-c%20timezone%3DUTC
R2DBC_URL=r2dbc:postgresql://<host>/llmgateway?sslMode=REQUIRE
```

### Step 2 — Backend: Render

1. Push the repo to GitHub.
2. Sign up at [render.com](https://render.com) → **New → Web Service** → connect the GitHub repo.
3. Set **Root Directory** to `backend`.
4. Set **Environment** to `Docker` — Render will use `backend/Dockerfile` automatically.
5. Set **Instance Type** to `Free`.
6. Add these environment variables in the Render dashboard:

| Variable | Value |
|---|---|
| `DB_USERNAME` | from Neon |
| `DB_PASSWORD` | from Neon |
| `JDBC_URL` | Neon JDBC URL (with `sslmode=require&TimeZone=UTC`) |
| `FLYWAY_URL` | Neon JDBC URL (with `sslmode=require&options=-c%20timezone%3DUTC`) |
| `R2DBC_URL` | `r2dbc:postgresql://<host>/llmgateway?sslMode=REQUIRE` |
| `JWT_SECRET` | any random 64-char string |
| `ENCRYPTION_KEY` | exactly 64 hex chars (32 random bytes, hex-encoded) |
| `OPENAI_API_KEY` | your key (optional) |
| `GEMINI_API_KEY` | your key (optional) |
| `ANTHROPIC_API_KEY` | your key (optional) |
| `CORS_ALLOWED_ORIGINS` | `https://your-app.vercel.app` (fill in after Vercel deploy) |

7. Deploy. First build takes ~5 minutes. Note the URL: `https://your-backend.onrender.com`.

Generate secrets:
```bash
# JWT_SECRET
openssl rand -hex 32

# ENCRYPTION_KEY
openssl rand -hex 32
```

### Step 3 — Frontend: Vercel

1. Sign up at [vercel.com](https://vercel.com) → **New Project** → import the GitHub repo.
2. Set **Root Directory** to `frontend`.
3. Vercel auto-detects Vite. Build command: `npm run build`. Output: `dist`.
4. Add one environment variable:

| Variable | Value |
|---|---|
| `VITE_API_URL` | `https://your-backend.onrender.com` |

5. Deploy. Note the URL: `https://your-app.vercel.app`.
6. Go back to Render → update `CORS_ALLOWED_ORIGINS` to `https://your-app.vercel.app` → redeploy.

### How the pieces connect in production

```
Browser
  │  HTTPS
  ▼
Vercel (React static bundle)
  │  fetch("https://your-backend.onrender.com/v1/...")
  │  VITE_API_URL tells the frontend where the backend is
  ▼
Render (Spring Boot Docker container)
  │  R2DBC / JDBC
  ▼
Neon (PostgreSQL, serverless)
```

In dev the Vite proxy handles `/v1/*` and `/auth/*` locally. In production there is no proxy — `VITE_API_URL` prefixes every API call with the Render URL directly.

### Key env var differences: local vs production

| Setting | Local | Production |
|---|---|---|
| `R2DBC_URL` | `r2dbc:postgresql://localhost:5432/llmgateway` | Neon R2DBC URL with `sslMode=REQUIRE` |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:*` (default) | `https://your-app.vercel.app` |
| `VITE_API_URL` | not set (Vite proxy handles it) | `https://your-backend.onrender.com` |
| `JWT_SECRET` | dev placeholder | strong random secret |
| `ENCRYPTION_KEY` | dev placeholder | strong random hex key |
