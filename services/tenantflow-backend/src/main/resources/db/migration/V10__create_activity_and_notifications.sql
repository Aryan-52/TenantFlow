-- Activity history: what changed in a workspace, shown to its members.
-- task_id carries no foreign key so the history of a deleted task survives (the summary
-- holds a snapshot of the task title); a deleted project only nulls project_id.
CREATE TABLE activity_events (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	tenant_id UUID NOT NULL,
	project_id UUID,
	task_id UUID,
	actor_id UUID,
	type VARCHAR(64) NOT NULL,
	summary VARCHAR(500) NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT fk_activity_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
	CONSTRAINT fk_activity_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE SET NULL,
	CONSTRAINT fk_activity_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_activity_tenant_time ON activity_events (tenant_id, created_at DESC);
CREATE INDEX idx_activity_project_time ON activity_events (project_id, created_at DESC);

-- In-app notifications. tenant_id is NULL for notices that must stay visible after the
-- recipient has left the workspace (e.g. "you were removed"). (recipient_id, dedupe_key)
-- is unique so a retried or replayed change can never notify the same person twice.
CREATE TABLE notifications (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	recipient_id UUID NOT NULL,
	tenant_id UUID,
	activity_id UUID,
	type VARCHAR(64) NOT NULL,
	title VARCHAR(200) NOT NULL,
	body VARCHAR(500),
	link VARCHAR(500),
	dedupe_key VARCHAR(200) NOT NULL,
	read_at TIMESTAMPTZ,
	created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT fk_notifications_recipient FOREIGN KEY (recipient_id) REFERENCES users (id) ON DELETE CASCADE,
	CONSTRAINT fk_notifications_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
	CONSTRAINT fk_notifications_activity FOREIGN KEY (activity_id) REFERENCES activity_events (id) ON DELETE SET NULL,
	CONSTRAINT uk_notifications_recipient_dedupe UNIQUE (recipient_id, dedupe_key)
);

CREATE INDEX idx_notifications_recipient_time ON notifications (recipient_id, created_at DESC);
CREATE INDEX idx_notifications_recipient_unread ON notifications (recipient_id) WHERE read_at IS NULL;
