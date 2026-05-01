-- pgcrypto provides digest() so the README seed can hash an API key in-band.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Align tenants with the Tenant entity, which already declares these columns.
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS monthly_token_budget BIGINT;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS webhook_url TEXT;
