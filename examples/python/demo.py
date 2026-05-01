"""
demo.py - End-to-end walkthrough of the LLM Gateway Python client.

Run:
    pip install requests
    python demo.py

Override defaults via env vars:
    GATEWAY_URL=http://localhost:8080
    GATEWAY_EMAIL=admin@gmail.com
    GATEWAY_PASSWORD=admin@123
"""

import os
import sys
import time
from datetime import date, timedelta
from client import GatewayClient, GatewayError


GATEWAY_URL = os.getenv("GATEWAY_URL", "http://localhost:8080")
EMAIL       = os.getenv("GATEWAY_EMAIL", "admin@gmail.com")
PASSWORD    = os.getenv("GATEWAY_PASSWORD", "admin@123")


def section(title: str):
    print(f"\n{'-' * 55}")
    print(f"  {title}")
    print("-" * 55)


def main():
    gw = GatewayClient(GATEWAY_URL)

    # -- 1. Register -----------------------------------------------------------
    section("1. Register")
    try:
        gw.register(EMAIL, PASSWORD)
        print(f"  Registered: {EMAIL}")
    except GatewayError as e:
        if "409" in str(e) or "already" in str(e).lower():
            print("  Account already exists -- skipping to login.")
        else:
            print(f"  Register failed: {e}")
            sys.exit(1)

    # -- 2. Login --------------------------------------------------------------
    section("2. Login")
    info = gw.login(EMAIL, PASSWORD)
    print(f"  Logged in  : {info['email']}")
    print(f"  Tenant ID  : {info['tenantId']}")

    # -- 3. Check provider keys ------------------------------------------------
    section("3. Provider keys (your stored keys)")
    keys = gw.list_provider_keys()
    for k in keys:
        status = "[ok] configured" if k["configured"] else "[ ] not set (using server default)"
        print(f"  {k['provider']:12} {status}")

    # -- 4. Raw message completion ---------------------------------------------
    section("4. Raw message - streaming response")
    print("  Prompt : 'In one sentence, what is a large language model?'")
    print("  Response: ", end="", flush=True)

    try:
        gw.complete(
            message="In one sentence, what is a large language model?",
            models=["gemini-2.5-flash", "gpt-4o-mini"],
            on_token=lambda t: print(t, end="", flush=True),
            on_status=lambda event, data: (
                print(f"\n  [{event}] {data}", flush=True)
                if event != "done"
                else print(f"\n  [done] served by {data}")
            ),
        )
    except GatewayError as e:
        print(f"\n  [skipped] {e}")
    print()
    time.sleep(2)

    # -- 5. Prompt template ----------------------------------------------------
    section("5. Prompt template with {{variables}}")

    # Create prompt + version (idempotent - skip if already exists)
    try:
        gw.create_prompt("explain")
    except GatewayError:
        pass
    try:
        gw.add_version(
            "explain",
            version="v1",
            template="Explain {{concept}} in one sentence, like I'm {{audience}}.",
            description="Simple explainer",
            is_active=False,
        )
        print("  Created prompt 'explain' v1")
    except GatewayError:
        print("  Prompt 'explain' v1 already exists.")

    # Always pin v1 as active so the demo is idempotent across reruns
    gw.set_active_version("explain", "v1")

    print("  Variables  : concept='gradient descent', audience='a 10-year-old'")
    print("  Response   : ", end="", flush=True)
    try:
        gw.complete(
            prompt_name="explain",
            version="v1",   # pin explicitly
            variables={"concept": "gradient descent", "audience": "a 10-year-old"},
            models=["gemini-2.5-flash"],
            on_token=lambda t: print(t, end="", flush=True),
        )
    except GatewayError as e:
        print(f"\n  [skipped] {e}")
    print()
    time.sleep(2)

    # -- 6. Add a new version + rollback --------------------------------------
    section("6. Prompt versioning - add v2 then roll back to v1")
    try:
        gw.add_version(
            "explain",
            version="v2",
            template="Give me a one-line ELI5 of {{concept}} for {{audience}}.",
            is_active=False,   # set active explicitly below
        )
        print("  Added v2")
    except GatewayError:
        print("  v2 already exists.")

    gw.set_active_version("explain", "v2")
    print("  Activated v2")

    print("  v2 response: ", end="", flush=True)
    try:
        gw.complete(
            prompt_name="explain",
            version="v2",   # pin to v2 so demo is idempotent across reruns
            variables={"concept": "gradient descent", "audience": "a 5-year-old"},
            models=["gemini-2.5-flash"],
            on_token=lambda t: print(t, end="", flush=True),
        )
    except GatewayError as e:
        print(f"\n  [skipped] {e}")
    print()

    gw.set_active_version("explain", "v1")
    print("  Rolled back to v1")
    time.sleep(2)

    # -- 7. Multi-model fallback chain -----------------------------------------
    section("7. Multi-model fallback chain")
    print("  Chain: gpt-4o -> gemini-2.5-flash -> claude-haiku-4-5-20251001")
    print("  Response: ", end="", flush=True)

    def show_status(event: str, data: str):
        icons = {"attempting": "-> trying", "fallback": "fallback from", "done": "served by"}
        print(f"\n  [{icons.get(event, event)}] {data}", flush=True)

    try:
        gw.complete(
            message="Say hello in exactly two words.",
            models=["gpt-4o", "gemini-2.5-flash", "claude-haiku-4-5-20251001"],
            on_token=lambda t: print(t, end="", flush=True),
            on_status=show_status,
        )
    except GatewayError as e:
        print(f"\n  [skipped] {e}")
    print()

    # -- 8. Usage / cost summary -----------------------------------------------
    section("8. Usage summary - last 7 days")
    to_date   = date.today().isoformat()
    from_date = (date.today() - timedelta(days=7)).isoformat()
    summary   = gw.usage_summary(from_date, to_date)

    print(f"  Requests   : {summary['totalRequests']}")
    print(f"  Total cost : ${summary['totalCostUsd']:.6f}")
    if summary["byProvider"]:
        for p in summary["byProvider"]:
            print(f"  {p['provider']:12} {p['requests']} req  ${p['costUsd']:.6f}")
    if summary["byModel"]:
        print()
        for m in summary["byModel"]:
            print(f"  {m['model']:30} ${m['costUsd']:.6f}")

    print("\n  All done.\n")


if __name__ == "__main__":
    main()
