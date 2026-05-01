-- Drop columns that exist in the schema but are never read or written by the application.

ALTER TABLE tenants DROP COLUMN IF EXISTS monthly_token_budget;
ALTER TABLE tenants DROP COLUMN IF EXISTS webhook_url;

-- prompt_versions.tags was declared in V1 but was never mapped to the PromptVersion entity
-- and is not referenced anywhere in application code.
ALTER TABLE prompt_versions DROP COLUMN IF EXISTS tags;

-- Add indexes for foreign key columns used in WHERE clauses.
-- prompts.tenant_id: used in PromptRepository.findByTenantIdAndName and findByTenantIdOrderByCreatedAtDesc
CREATE INDEX IF NOT EXISTS idx_prompts_tenant_id ON prompts(tenant_id);

-- usage_events.tenant_id + created_at: all three UsageService summary queries filter by
-- tenant_id and order/filter by created_at — a composite index covers both.
CREATE INDEX IF NOT EXISTS idx_usage_events_tenant_created ON usage_events(tenant_id, created_at);
