CREATE TABLE sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL DEFAULT 'Session',
    started_at  TIMESTAMP NOT NULL DEFAULT now(),
    ended_at    TIMESTAMP
);

CREATE INDEX idx_sessions_tenant_id ON sessions(tenant_id);
CREATE INDEX idx_sessions_tenant_active ON sessions(tenant_id) WHERE ended_at IS NULL;

ALTER TABLE usage_events ADD COLUMN session_id UUID REFERENCES sessions(id) ON DELETE SET NULL;
CREATE INDEX idx_usage_events_session_id ON usage_events(session_id);
