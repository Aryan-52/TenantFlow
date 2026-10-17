-- Task discussion. Additive only: a new table, no change to existing tables or data.
-- Comments belong to a task (deleted with it); deleting a user keeps their comments
-- with an unknown author. tenant_id is stored so every query can be tenant-scoped.
CREATE TABLE task_comments (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	tenant_id UUID NOT NULL,
	task_id UUID NOT NULL,
	author_id UUID,
	body VARCHAR(4000) NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT ck_task_comments_body CHECK (length(btrim(body)) > 0),
	CONSTRAINT fk_task_comments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
	CONSTRAINT fk_task_comments_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
	CONSTRAINT fk_task_comments_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_task_comments_task_time ON task_comments (task_id, created_at);

-- Task history is read per task (activity_events already records task_id).
CREATE INDEX idx_activity_task_time ON activity_events (task_id, created_at DESC) WHERE task_id IS NOT NULL;
