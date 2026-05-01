"""
LLM Gateway — Python client.

Requires: pip install requests
"""

import base64
import json
import os
import time
from pathlib import Path
from typing import Callable, Optional
import requests


class GatewayClient:
    """
    Thin client for the LLM Gateway API.

    Option A — JWT auth (supports per-user provider keys):
        gw = GatewayClient("http://localhost:8080")
        gw.login("alice@example.com", "secret123")   # once — token cached for 24h
        text = gw.complete(message="Hello!", models=["gemini-2.5-flash"])

    Option B — JWT with auto token caching across process restarts:
        gw = GatewayClient("http://localhost:8080")
        gw.ensure_logged_in("alice@example.com", "secret123")  # only re-logins when token expired
        text = gw.complete(message="Hello!", models=["gemini-2.5-flash"])

    Option C — API key auth (never expires, no login needed):
        gw = GatewayClient("http://localhost:8080", api_key="llm-gateway-dev-key")
        text = gw.complete(message="Hello!", models=["gemini-2.5-flash"])
        # Note: uses server-level provider keys, not per-user keys
    """

    def __init__(self, base_url: str = "http://localhost:8080", api_key: Optional[str] = None):
        self.base_url = base_url.rstrip("/")
        self._api_key = api_key   # static X-API-Key — never expires
        self._token: Optional[str] = None

    # ── Auth ──────────────────────────────────────────────────────────────────

    def register(self, email: str, password: str) -> dict:
        """
        Register a new account. Does NOT log in automatically — call login() next.
        Raises GatewayError on failure (e.g. 409 if email already exists).
        """
        r = requests.post(
            f"{self.base_url}/auth/register",
            json={"email": email.strip().lower(), "password": password},
        )
        _raise(r)
        return r.json()

    def login(self, email: str, password: str) -> dict:
        """Log in and store the JWT for 24 hours. Returns {token, userId, tenantId, email}."""
        r = requests.post(
            f"{self.base_url}/auth/login",
            json={"email": email.strip().lower(), "password": password},
        )
        _raise(r)
        data = r.json()
        self._token = data["token"]
        return data

    def ensure_logged_in(
        self,
        email: str,
        password: str,
        token_file: str = ".gateway_token",
    ) -> None:
        """
        Log in only when necessary — restores a cached token from disk if it is
        still valid, otherwise re-authenticates and saves the new token.

        Call this once at the top of your script instead of login().
        The token file is safe to .gitignore.
        """
        path = Path(token_file)
        if path.exists():
            token = path.read_text().strip()
            if token and not _token_expired(token):
                self._token = token
                return
        data = self.login(email, password)
        path.write_text(data["token"])

    def logout(self):
        """Discard the stored JWT. Best-effort server notification."""
        try:
            requests.post(f"{self.base_url}/auth/logout", headers=self._headers())
        except Exception:
            pass
        self._token = None

    # ── Provider keys ─────────────────────────────────────────────────────────

    def list_provider_keys(self) -> list[dict]:
        """Returns [{provider, configured, updatedAt}]. Never returns plaintext keys."""
        r = requests.get(f"{self.base_url}/v1/user/provider-keys", headers=self._headers())
        _raise(r)
        return r.json()

    def set_provider_key(self, provider: str, api_key: str) -> None:
        """
        Save or replace your API key for a provider.
        provider: 'openai' | 'gemini' | 'anthropic'
        Keys are encrypted at rest — the gateway never stores plaintext.
        """
        r = requests.put(
            f"{self.base_url}/v1/user/provider-keys/{provider}",
            json={"apiKey": api_key},
            headers=self._headers(),
        )
        _raise(r)

    def delete_provider_key(self, provider: str) -> None:
        """Remove your stored key. Gateway falls back to server-level env var."""
        r = requests.delete(
            f"{self.base_url}/v1/user/provider-keys/{provider}",
            headers=self._headers(),
        )
        _raise(r)

    # ── Completions ───────────────────────────────────────────────────────────

    def complete(
        self,
        *,
        message: Optional[str] = None,
        models: list[str],
        prompt_name: Optional[str] = None,
        version: Optional[str] = None,
        variables: Optional[dict[str, str]] = None,
        on_token: Optional[Callable[[str], None]] = None,
        on_status: Optional[Callable[[str, str], None]] = None,
    ) -> str:
        """
        Stream a completion and return the full concatenated text.

        Provide either `message` (raw text) or `prompt_name` + optional `variables`.
        `models` is a priority-ordered fallback list — the gateway tries each in order.

        Callbacks (optional):
          on_token(text)         — fired for each streamed chunk
          on_status(event, data) — fired for 'attempting', 'fallback', 'done' events

        Model IDs:
          OpenAI    : gpt-4o, gpt-4o-mini
          Gemini    : gemini-2.5-flash, gemini-flash-latest
          Anthropic : claude-sonnet-4-6, claude-haiku-4-5-20251001, claude-opus-4-7
        """
        if not message and not prompt_name:
            raise ValueError("Provide either message= or prompt_name=")

        payload: dict = {"models": models}
        if message:
            payload["message"] = message
        if prompt_name:
            payload["promptName"] = prompt_name
        if version:
            payload["version"] = version
        if variables:
            payload["variables"] = variables

        headers = {**self._headers(), "Accept": "text/event-stream"}
        with requests.post(
            f"{self.base_url}/v1/completions",
            json=payload,
            headers=headers,
            stream=True,
        ) as r:
            _raise(r)
            return _consume_sse(r, on_token=on_token, on_status=on_status)

    # ── Prompts ───────────────────────────────────────────────────────────────

    def create_prompt(self, name: str) -> dict:
        """Create a new named prompt. Returns {id, name, tenantId, createdAt}."""
        r = requests.post(
            f"{self.base_url}/v1/prompts",
            json={"name": name},
            headers=self._headers(),
        )
        _raise(r)
        return r.json()

    def list_versions(self, prompt_name: str) -> list[dict]:
        """List all versions of a prompt."""
        r = requests.get(
            f"{self.base_url}/v1/prompts/{prompt_name}/versions",
            headers=self._headers(),
        )
        _raise(r)
        return r.json()

    def add_version(
        self,
        prompt_name: str,
        version: str,
        template: str,
        description: Optional[str] = None,
        is_active: bool = True,
    ) -> dict:
        """
        Add a versioned template to a prompt.
        Use {{variable_name}} placeholders in the template string.
        """
        payload = {"version": version, "template": template, "isActive": is_active}
        if description:
            payload["description"] = description
        r = requests.post(
            f"{self.base_url}/v1/prompts/{prompt_name}/versions",
            json=payload,
            headers=self._headers(),
        )
        _raise(r)
        return r.json()

    def set_active_version(self, prompt_name: str, version: str) -> dict:
        """Switch the active version of a prompt (instant rollback)."""
        r = requests.put(
            f"{self.base_url}/v1/prompts/{prompt_name}/active-version",
            json={"version": version},
            headers=self._headers(),
        )
        _raise(r)
        return r.json()

    # ── Usage ─────────────────────────────────────────────────────────────────

    def usage_summary(self, from_date: str, to_date: str) -> dict:
        """
        Returns cost + token usage summary for the given date range.
        Dates are 'YYYY-MM-DD' strings.
        """
        r = requests.get(
            f"{self.base_url}/v1/usage/summary",
            params={"from": from_date, "to": to_date},
            headers=self._headers(),
        )
        _raise(r)
        return r.json()

    # ── Internal ──────────────────────────────────────────────────────────────

    def _headers(self) -> dict:
        h = {"Content-Type": "application/json"}
        if self._api_key:
            h["X-API-Key"] = self._api_key
        elif self._token:
            h["Authorization"] = f"Bearer {self._token}"
        return h


# ── Helpers ───────────────────────────────────────────────────────────────────

def _consume_sse(
    response: requests.Response,
    on_token: Optional[Callable[[str], None]],
    on_status: Optional[Callable[[str, str], None]],
) -> str:
    """Parse the SSE stream from the gateway and return the full response text."""
    chunks: list[str] = []
    current_event: Optional[str] = None

    for raw in response.iter_lines():
        line = raw.decode("utf-8") if isinstance(raw, bytes) else raw

        if line.startswith("event:"):
            current_event = line[len("event:"):].strip()
        elif line.startswith("data:"):
            data = line[len("data:"):].strip()
            if current_event == "token":
                chunks.append(data)
                if on_token:
                    on_token(data)
            elif current_event == "error":
                raise GatewayError(f"Gateway error: {data}")
            elif current_event in ("attempting", "fallback", "done"):
                if on_status:
                    on_status(current_event, data)
            current_event = None

    return "".join(chunks)


def _token_expired(token: str) -> bool:
    """Return True if the JWT is expired or unreadable."""
    try:
        payload_part = token.split(".")[1]
        # JWT uses base64url — add padding if needed
        padded = payload_part + "=" * (-len(payload_part) % 4)
        payload = json.loads(base64.urlsafe_b64decode(padded))
        return time.time() >= payload["exp"]
    except Exception:
        return True   # treat unreadable tokens as expired


def _raise(response: requests.Response) -> None:
    if response.ok:
        return
    try:
        detail = response.json()
    except Exception:
        detail = response.text
    raise GatewayError(f"HTTP {response.status_code}: {detail}")


class GatewayError(Exception):
    pass
