-- Dev seed: one tenant + one known API key for local testing.
-- Plaintext key: llm-gateway-dev-key
-- Hash computed inline via pgcrypto (enabled in V2) — matches AuthFilter's SHA-256 / %02x output.

INSERT INTO tenants (id, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'dev-tenant')
ON CONFLICT DO NOTHING;

INSERT INTO api_keys (tenant_id, key_hash)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    encode(digest('llm-gateway-dev-key', 'sha256'), 'hex')
)
ON CONFLICT DO NOTHING;

-- Seed a starter prompt so the gateway can be curled without extra setup.
INSERT INTO prompts (id, tenant_id, name)
VALUES ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'hello')
ON CONFLICT DO NOTHING;

INSERT INTO prompt_versions (prompt_id, version, template, description, is_active)
VALUES (
    '00000000-0000-0000-0000-000000000002',
    'v1',
    'Say hello to {{name}} in one sentence.',
    'Dev seed prompt',
    true
)
ON CONFLICT DO NOTHING;
