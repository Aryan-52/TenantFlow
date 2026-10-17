-- Append-only audit trail for security-sensitive and administrative actions.
-- tenant_id / actor_id are deliberately NOT foreign keys: audit records must survive the
-- deletion of the workspace or user they describe. Never store passwords, tokens or links.
CREATE TABLE audit_events (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	tenant_id UUID,
	actor_id UUID,
	actor_email VARCHAR(320),
	action VARCHAR(64) NOT NULL,
	outcome VARCHAR(16) NOT NULL,
	target_type VARCHAR(32),
	target_id UUID,
	details VARCHAR(1000),
	ip_address VARCHAR(64),
	request_id VARCHAR(64),
	CONSTRAINT ck_audit_events_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);

CREATE INDEX idx_audit_events_tenant_time ON audit_events (tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_events_actor_time ON audit_events (actor_id, occurred_at DESC);
