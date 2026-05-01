import { useState } from 'react'
import { API_BASE } from '../api'

const BASE = API_BASE || 'https://your-backend.onrender.com'

type Lang = 'cURL' | 'Python' | 'TypeScript' | 'Java' | 'Go' | 'PHP'
const LANGS: Lang[] = ['cURL', 'Python', 'TypeScript', 'Java', 'Go', 'PHP']

interface Snippet {
  install?: string
  auth: string
  complete: string
  fallback: string
  prompt: string
}

function snippets(base: string): Record<Lang, Snippet> {
  return {
    cURL: {
      auth: `# Login and capture the JWT
TOKEN=$(curl -s -X POST ${base}/auth/login \\
  -H "Content-Type: application/json" \\
  -d '{"email":"you@example.com","password":"yourpassword"}' | jq -r .token)

echo $TOKEN`,
      complete: `# Send a completion (streams SSE tokens)
curl -N -X POST ${base}/v1/completions \\
  -H "Authorization: Bearer $TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '{"message":"Explain async/await in one sentence.","models":["gpt-4o-mini"]}'`,
      fallback: `# Fallback chain — tries GPT first, falls back to Gemini if it fails
curl -N -X POST ${base}/v1/completions \\
  -H "Authorization: Bearer $TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '{"message":"Hello!","models":["gpt-4o-mini","gemini-2.5-flash","claude-haiku-4-5-20251001"]}'`,
      prompt: `# Use a versioned prompt template
curl -N -X POST ${base}/v1/completions \\
  -H "Authorization: Bearer $TOKEN" \\
  -H "Content-Type: application/json" \\
  -d '{"promptName":"summarize","variables":{"content":"your text here"},"models":["gpt-4o-mini"]}'`,
    },

    Python: {
      install: `pip install requests`,
      auth: `# Copy examples/python/client.py into your project
from client import GatewayClient

gw = GatewayClient("${base}")
gw.login("you@example.com", "yourpassword")

# For long-running services — caches token to disk, skips login if still valid
# gw.ensure_logged_in("you@example.com", "yourpassword")`,
      complete: `# Simple completion — returns the full response string
response = gw.complete(
    message="Explain async/await in one sentence.",
    models=["gpt-4o-mini"],
)
print(response)`,
      fallback: `# Fallback chain — tries each model left to right until one succeeds
response = gw.complete(
    message="Hello!",
    models=["gpt-4o-mini", "gemini-2.5-flash", "claude-haiku-4-5-20251001"],
    on_token=lambda t: print(t, end="", flush=True),   # stream live
    on_status=lambda event, data: print(f"[{event}] {data}"),
)`,
      prompt: `# Use a versioned prompt template with variables
response = gw.complete(
    prompt_name="summarize",
    variables={"content": "your long article text here"},
    models=["gemini-2.5-flash"],
)
print(response)`,
    },

    TypeScript: {
      install: `# Node 20+ — fetch is built-in, no extra packages needed
# Copy examples/node/client.ts into your project`,
      auth: `import { GatewayClient } from "./client"

const gw = new GatewayClient("${base}")
await gw.login("you@example.com", "yourpassword")

// For long-running services — caches token to .gateway_token, skips re-login
// await gw.ensureLoggedIn("you@example.com", "yourpassword")`,
      complete: `// Simple completion
const response = await gw.complete({
  message: "Explain async/await in one sentence.",
  models: ["gpt-4o-mini"],
})
console.log(response)`,
      fallback: `// Fallback chain with live streaming
const response = await gw.complete({
  message: "Hello!",
  models: ["gpt-4o-mini", "gemini-2.5-flash", "claude-haiku-4-5-20251001"],
  onToken: (t) => process.stdout.write(t),
  onStatus: (event, data) => console.log(\`[\${event}] \${data}\`),
})`,
      prompt: `// Use a versioned prompt template with variables
const response = await gw.complete({
  promptName: "summarize",
  variables: { content: "your long article text here" },
  models: ["gemini-2.5-flash"],
})
console.log(response)`,
    },

    Java: {
      install: `<!-- pom.xml -->
<dependency>
  <groupId>com.squareup.okhttp3</groupId>
  <artifactId>okhttp</artifactId>
  <version>4.12.0</version>
</dependency>
<dependency>
  <groupId>org.json</groupId>
  <artifactId>json</artifactId>
  <version>20240303</version>
</dependency>`,
      auth: `import okhttp3.*;
import org.json.JSONObject;

OkHttpClient client = new OkHttpClient();
MediaType JSON = MediaType.parse("application/json");

// Login
String loginBody = new JSONObject()
    .put("email", "you@example.com")
    .put("password", "yourpassword")
    .toString();

Request loginReq = new Request.Builder()
    .url("${base}/auth/login")
    .post(RequestBody.create(loginBody, JSON))
    .build();

Response loginResp = client.newCall(loginReq).execute();
String token = new JSONObject(loginResp.body().string()).getString("token");`,
      complete: `// Send a completion
String body = new JSONObject()
    .put("message", "Explain async/await in one sentence.")
    .put("models", new String[]{"gpt-4o-mini"})
    .toString();

Request req = new Request.Builder()
    .url("${base}/v1/completions")
    .post(RequestBody.create(body, JSON))
    .header("Authorization", "Bearer " + token)
    .header("Accept", "text/event-stream")
    .build();

Response resp = client.newCall(req).execute();
System.out.println(resp.body().string());`,
      fallback: `// Fallback chain — gateway tries each model in order
String body = new JSONObject()
    .put("message", "Hello!")
    .put("models", new String[]{
        "gpt-4o-mini", "gemini-2.5-flash", "claude-haiku-4-5-20251001"
    })
    .toString();

Request req = new Request.Builder()
    .url("${base}/v1/completions")
    .post(RequestBody.create(body, JSON))
    .header("Authorization", "Bearer " + token)
    .header("Accept", "text/event-stream")
    .build();`,
      prompt: `// Use a versioned prompt template
String body = new JSONObject()
    .put("promptName", "summarize")
    .put("variables", new JSONObject().put("content", "your text here"))
    .put("models", new String[]{"gpt-4o-mini"})
    .toString();

Request req = new Request.Builder()
    .url("${base}/v1/completions")
    .post(RequestBody.create(body, JSON))
    .header("Authorization", "Bearer " + token)
    .header("Accept", "text/event-stream")
    .build();`,
    },

    Go: {
      install: `# No extra packages — uses stdlib only`,
      auth: `package main

import (
    "bytes"
    "encoding/json"
    "io"
    "net/http"
)

// Login
loginPayload, _ := json.Marshal(map[string]string{
    "email":    "you@example.com",
    "password": "yourpassword",
})
resp, _ := http.Post("${base}/auth/login",
    "application/json", bytes.NewBuffer(loginPayload))
body, _ := io.ReadAll(resp.Body)

var loginResp map[string]interface{}
json.Unmarshal(body, &loginResp)
token := loginResp["token"].(string)`,
      complete: `// Send a completion
payload, _ := json.Marshal(map[string]interface{}{
    "message": "Explain async/await in one sentence.",
    "models":  []string{"gpt-4o-mini"},
})

req, _ := http.NewRequest("POST", "${base}/v1/completions",
    bytes.NewBuffer(payload))
req.Header.Set("Authorization", "Bearer "+token)
req.Header.Set("Content-Type", "application/json")
req.Header.Set("Accept", "text/event-stream")

resp, _ := http.DefaultClient.Do(req)
body, _ := io.ReadAll(resp.Body)
fmt.Println(string(body))`,
      fallback: `// Fallback chain — gateway tries each model in order
payload, _ := json.Marshal(map[string]interface{}{
    "message": "Hello!",
    "models":  []string{"gpt-4o-mini", "gemini-2.5-flash", "claude-haiku-4-5-20251001"},
})

req, _ := http.NewRequest("POST", "${base}/v1/completions",
    bytes.NewBuffer(payload))
req.Header.Set("Authorization", "Bearer "+token)
req.Header.Set("Content-Type", "application/json")
req.Header.Set("Accept", "text/event-stream")

resp, _ := http.DefaultClient.Do(req)`,
      prompt: `// Use a versioned prompt template
payload, _ := json.Marshal(map[string]interface{}{
    "promptName": "summarize",
    "variables":  map[string]string{"content": "your text here"},
    "models":     []string{"gemini-2.5-flash"},
})

req, _ := http.NewRequest("POST", "${base}/v1/completions",
    bytes.NewBuffer(payload))
req.Header.Set("Authorization", "Bearer "+token)
req.Header.Set("Content-Type", "application/json")`,
    },

    PHP: {
      install: `# Uses built-in cURL extension (enabled by default in most PHP installs)`,
      auth: `<?php
// Login
$ch = curl_init("${base}/auth/login");
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_HTTPHEADER => ["Content-Type: application/json"],
    CURLOPT_POSTFIELDS => json_encode([
        "email"    => "you@example.com",
        "password" => "yourpassword",
    ]),
]);
$resp = json_decode(curl_exec($ch), true);
curl_close($ch);
$token = $resp["token"];`,
      complete: `// Send a completion
$ch = curl_init("${base}/v1/completions");
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_HTTPHEADER => [
        "Content-Type: application/json",
        "Accept: text/event-stream",
        "Authorization: Bearer " . $token,
    ],
    CURLOPT_POSTFIELDS => json_encode([
        "message" => "Explain async/await in one sentence.",
        "models"  => ["gpt-4o-mini"],
    ]),
]);
$response = curl_exec($ch);
curl_close($ch);
echo $response;`,
      fallback: `// Fallback chain — gateway tries each model in order
$ch = curl_init("${base}/v1/completions");
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_HTTPHEADER => [
        "Content-Type: application/json",
        "Accept: text/event-stream",
        "Authorization: Bearer " . $token,
    ],
    CURLOPT_POSTFIELDS => json_encode([
        "message" => "Hello!",
        "models"  => ["gpt-4o-mini", "gemini-2.5-flash", "claude-haiku-4-5-20251001"],
    ]),
]);
$response = curl_exec($ch);
curl_close($ch);`,
      prompt: `// Use a versioned prompt template
$ch = curl_init("${base}/v1/completions");
curl_setopt_array($ch, [
    CURLOPT_POST => true,
    CURLOPT_RETURNTRANSFER => true,
    CURLOPT_HTTPHEADER => [
        "Content-Type: application/json",
        "Accept: text/event-stream",
        "Authorization: Bearer " . $token,
    ],
    CURLOPT_POSTFIELDS => json_encode([
        "promptName" => "summarize",
        "variables"  => ["content" => "your text here"],
        "models"     => ["gpt-4o-mini"],
    ]),
]);
$response = curl_exec($ch);
curl_close($ch);`,
    },
  }
}

function CodeBlock({ code, id, copied, onCopy }: {
  code: string
  id: string
  copied: string | null
  onCopy: (code: string, id: string) => void
}) {
  return (
    <div className="instr-code-wrap">
      <button
        className={`instr-copy-btn ${copied === id ? 'copied' : ''}`}
        onClick={() => onCopy(code, id)}
      >
        {copied === id ? 'Copied!' : 'Copy'}
      </button>
      <pre className="instr-code"><code>{code}</code></pre>
    </div>
  )
}

export default function Instructions() {
  const [lang, setLang] = useState<Lang>('Python')
  const [copied, setCopied] = useState<string | null>(null)

  function copy(code: string, id: string) {
    navigator.clipboard.writeText(code).catch(() => {})
    setCopied(id)
    setTimeout(() => setCopied(null), 1500)
  }

  const s = snippets(BASE)[lang]

  return (
    <div className="instr-page">
      <div className="instr-header">
        <h2 className="section-title">Integrate into your codebase</h2>
        <p className="section-sub">
          Use LLMetrics as a drop-in LLM gateway from any language. All requests go through
          your backend, so costs and latency are tracked automatically.
        </p>
        <div className="instr-base-url">
          <span className="instr-base-label">Backend URL</span>
          <code className="instr-base-value">{BASE}</code>
          <button
            className={`instr-copy-btn ${copied === 'base' ? 'copied' : ''}`}
            onClick={() => copy(BASE, 'base')}
          >
            {copied === 'base' ? 'Copied!' : 'Copy'}
          </button>
        </div>
      </div>

      <div className="instr-lang-bar">
        {LANGS.map(l => (
          <button
            key={l}
            className={`instr-lang-btn ${lang === l ? 'active' : ''}`}
            onClick={() => setLang(l)}
          >
            {l}
          </button>
        ))}
      </div>

      <div className="instr-sections">
        {s.install && (
          <div className="instr-section">
            <div className="instr-section-title">Install</div>
            <CodeBlock code={s.install} id="install" copied={copied} onCopy={copy} />
          </div>
        )}

        <div className="instr-section">
          <div className="instr-section-title">Authenticate</div>
          <p className="instr-section-desc">
            Register at <strong>{BASE.replace('https://', '')}</strong>, then log in from your code to get a JWT valid for 24 hours.
          </p>
          <CodeBlock code={s.auth} id="auth" copied={copied} onCopy={copy} />
        </div>

        <div className="instr-section">
          <div className="instr-section-title">Send a completion</div>
          <p className="instr-section-desc">
            Pass a message and a list of models. The gateway streams back SSE tokens and logs cost + latency automatically.
          </p>
          <CodeBlock code={s.complete} id="complete" copied={copied} onCopy={copy} />
        </div>

        <div className="instr-section">
          <div className="instr-section-title">Fallback chain</div>
          <p className="instr-section-desc">
            Pass multiple models in priority order. If the first fails (rate limit, outage, quota), the gateway silently retries the next — before any tokens reach your client.
          </p>
          <CodeBlock code={s.fallback} id="fallback" copied={copied} onCopy={copy} />
        </div>

        <div className="instr-section">
          <div className="instr-section-title">Prompt templates</div>
          <p className="instr-section-desc">
            Create versioned prompt templates in the <strong>Prompts</strong> tab using <code className="instr-inline-code">{'{{'} variable {'}}'}</code> placeholders.
            Reference them by name from your code — change the prompt without redeploying.
          </p>
          <CodeBlock code={s.prompt} id="prompt" copied={copied} onCopy={copy} />
        </div>

        <div className="instr-section">
          <div className="instr-section-title">Supported models</div>
          <div className="instr-model-table">
            <table>
              <thead>
                <tr><th>Model ID</th><th>Provider</th></tr>
              </thead>
              <tbody>
                {[
                  ['gpt-4o', 'OpenAI'],
                  ['gpt-4o-mini', 'OpenAI'],
                  ['gemini-2.5-flash', 'Google'],
                  ['gemini-flash-latest', 'Google'],
                  ['claude-sonnet-4-6', 'Anthropic'],
                  ['claude-haiku-4-5-20251001', 'Anthropic'],
                  ['claude-opus-4-7', 'Anthropic'],
                ].map(([model, provider]) => (
                  <tr key={model}>
                    <td><code className="instr-inline-code">{model}</code></td>
                    <td><span className="badge badge-muted">{provider}</span></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  )
}
