/**
 * demo.ts — End-to-end walkthrough of the LLM Gateway TypeScript client.
 *
 * Requirements: Node 20+
 * Run:          npx tsx demo.ts
 *
 * Override defaults:
 *   GATEWAY_URL=http://localhost:8080 GATEWAY_EMAIL=you@example.com npx tsx demo.ts
 */

import { GatewayClient, GatewayError } from "./client.ts"

const GATEWAY_URL = process.env.GATEWAY_URL      ?? "http://localhost:8080"
const EMAIL       = process.env.GATEWAY_EMAIL    ?? "demo-node@example.com"
const PASSWORD    = process.env.GATEWAY_PASSWORD ?? "demo-password-123"

function section(title: string) {
  console.log(`\n${"-".repeat(55)}`)
  console.log(`  ${title}`)
  console.log("-".repeat(55))
}

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

async function main() {
  const gw = new GatewayClient(GATEWAY_URL)

  // -- 1. Register ---------------------------------------------------------
  section("1. Register")
  try {
    await gw.register(EMAIL, PASSWORD)
    console.log(`  Registered: ${EMAIL}`)
  } catch (e) {
    if (e instanceof GatewayError && (e.message.includes("409") || e.message.toLowerCase().includes("already"))) {
      console.log("  Account already exists — skipping to login.")
    } else {
      console.error("  Register failed:", e)
      process.exit(1)
    }
  }

  // -- 2. Login -------------------------------------------------------------
  section("2. Login")
  const info = await gw.login(EMAIL, PASSWORD)
  console.log(`  Logged in  : ${info.email}`)
  console.log(`  Tenant ID  : ${info.tenantId}`)

  // -- 3. Check provider keys -----------------------------------------------
  section("3. Provider keys")
  const keys = await gw.listProviderKeys()
  for (const k of keys) {
    const status = k.configured ? "✓ configured" : "✗ not set (using server default)"
    console.log(`  ${k.provider.padEnd(12)} ${status}`)
  }

  // -- 4. Raw message completion --------------------------------------------
  section("4. Raw message — streaming response")
  console.log("  Prompt  : 'In one sentence, what is a large language model?'")
  process.stdout.write("  Response: ")

  await gw.complete({
    message: "In one sentence, what is a large language model?",
    models: ["gemini-2.5-flash", "gpt-4o-mini"],
    onToken: (t) => process.stdout.write(t),
    onStatus: (event, data) => {
      if (event === "done") console.log(`\n  [✓ served by ${data}]`)
      else                  console.log(`\n  [${event}] ${data}`)
    },
  })
  console.log()
  await sleep(1500)

  // -- 5. Prompt template ---------------------------------------------------
  section("5. Prompt template with {{variables}}")

  try { await gw.createPrompt("explain") } catch { /* already exists */ }
  try {
    await gw.addVersion("explain", {
      version: "v1",
      template: "Explain {{concept}} in one sentence, like I'm {{audience}}.",
      description: "Simple explainer",
      isActive: false,
    })
    console.log("  Created prompt 'explain' v1")
  } catch { console.log("  Prompt 'explain' v1 already exists.") }

  // Always pin v1 as active so the demo is idempotent across reruns
  await gw.setActiveVersion("explain", "v1")

  console.log("  Variables : concept='gradient descent', audience='a 10-year-old'")
  process.stdout.write("  Response  : ")
  await gw.complete({
    promptName: "explain",
    version: "v1",   // pin explicitly — don't rely on active-version resolution
    variables: { concept: "gradient descent", audience: "a 10-year-old" },
    models: ["gemini-2.5-flash"],
    onToken: (t) => process.stdout.write(t),
  })
  console.log()
  await sleep(1500)

  // -- 6. Add v2, stream it, roll back -------------------------------------
  section("6. Add v2, use it, roll back to v1")

  try {
    await gw.addVersion("explain", {
      version: "v2",
      template: "Give a one-line ELI5 of {{concept}} for {{audience}}.",
      isActive: false,   // we set it active explicitly below
    })
    console.log("  Added v2")
  } catch {
    console.log("  v2 already exists.")
  }

  // Always set explicitly so the demo is idempotent across reruns
  await gw.setActiveVersion("explain", "v2")
  console.log("  Activated v2")

  process.stdout.write("  v2 response: ")
  await gw.complete({
    promptName: "explain",
    version: "v2",     // pin to v2 — don't rely on active-version resolution
    variables: { concept: "transformer attention", audience: "a curious dog" },
    models: ["gemini-2.5-flash"],
    onToken: (t) => process.stdout.write(t),
  })
  console.log()

  await gw.setActiveVersion("explain", "v1")
  console.log("  Rolled back to v1")
  await sleep(1500)

  // -- 7. Multi-model fallback ----------------------------------------------
  section("7. Multi-model fallback chain")
  console.log("  Chain: gpt-4o → gemini-2.5-flash → claude-haiku-4-5-20251001\n")
  process.stdout.write("  Response: ")

  await gw.complete({
    message: "Say hello in exactly two words.",
    models: ["gpt-4o", "gemini-2.5-flash", "claude-haiku-4-5-20251001"],
    onToken: (t) => process.stdout.write(t),
    onStatus: (event, data) => {
      const label = { attempting: "→ trying", fallback: "↷ fell back from", done: "✓ served by" }[event]
      console.log(`\n  [${label}] ${data}`)
    },
  })
  console.log()

  // -- 8. Usage summary ----------------------------------------------------
  section("8. Usage summary — last 7 days")
  const today   = new Date()
  const weekAgo = new Date(today)
  weekAgo.setDate(today.getDate() - 7)
  const fmt = (d: Date) => d.toISOString().slice(0, 10)

  const summary = await gw.usageSummary(fmt(weekAgo), fmt(today))
  console.log(`  Requests   : ${summary.totalRequests}`)
  console.log(`  Total cost : $${summary.totalCostUsd.toFixed(6)}`)
  for (const p of summary.byProvider) {
    console.log(`  ${p.provider.padEnd(12)} ${p.requests} req   $${p.costUsd.toFixed(6)}`)
  }
  for (const m of summary.byModel) {
    console.log(`  ${m.model.padEnd(32)} $${m.costUsd.toFixed(6)}`)
  }

  console.log("\n  ✓ All done.\n")
}

main().catch((e) => {
  console.error("\nDemo failed:", e instanceof GatewayError ? e.message : e)
  process.exit(1)
})
