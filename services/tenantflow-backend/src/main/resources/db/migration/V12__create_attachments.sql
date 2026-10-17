-- File attachments on projects and tasks. Only metadata lives here; the bytes live in
-- S3-compatible object storage under a server-generated key (never a user-supplied path).
CREATE TABLE attachments (
	id UUID PRIMARY KEY,
	tenant_id UUID NOT NULL,
	project_id UUID NOT NULL,
	task_id UUID,
	uploaded_by UUID,
	original_filename VARCHAR(255) NOT NULL,
	content_type VARCHAR(127) NOT NULL,
	size_bytes BIGINT NOT NULL,
	sha256 VARCHAR(64) NOT NULL,
	storage_key VARCHAR(500) NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT uk_attachments_storage_key UNIQUE (storage_key),
	CONSTRAINT ck_attachments_size CHECK (size_bytes >= 0),
	CONSTRAINT fk_attachments_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
	CONSTRAINT fk_attachments_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
	CONSTRAINT fk_attachments_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
	CONSTRAINT fk_attachments_uploader FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_attachments_project_time ON attachments (project_id, created_at DESC);
CREATE INDEX idx_attachments_task ON attachments (task_id);
