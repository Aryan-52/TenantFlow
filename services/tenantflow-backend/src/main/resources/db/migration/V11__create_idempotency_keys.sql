-- Client-supplied Idempotency-Key for create requests: a retried or double-submitted
-- create returns the original resource instead of creating a duplicate. Rows are only
-- needed for a short replay window and are purged after 24 hours.
CREATE TABLE idempotency_keys (
	user_id UUID NOT NULL,
	scope VARCHAR(100) NOT NULL,
	idempotency_key VARCHAR(100) NOT NULL,
	request_hash VARCHAR(64) NOT NULL,
	resource_id UUID,
	created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT pk_idempotency_keys PRIMARY KEY (user_id, scope, idempotency_key),
	CONSTRAINT fk_idempotency_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_idempotency_keys_created ON idempotency_keys (created_at);
