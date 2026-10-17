-- Optimistic locking: concurrent edits of the same project/task fail with 409 instead of
-- silently overwriting each other. Existing rows start at version 0.
ALTER TABLE projects ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tasks ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
