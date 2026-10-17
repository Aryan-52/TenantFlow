-- Project owner / lead: an assignable workspace member, separate from created_by (who
-- created the project, which never changes). Nullable = unassigned; deleting the user
-- unassigns rather than deleting the project.
ALTER TABLE projects ADD COLUMN owner_id UUID;
ALTER TABLE projects ADD CONSTRAINT fk_projects_owner
	FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX idx_projects_owner_id ON projects (owner_id);

-- Existing projects: the creator becomes the owner, but only while they are still a
-- member of the project's workspace. Everything else stays unassigned.
UPDATE projects p
SET owner_id = p.created_by
WHERE p.created_by IS NOT NULL
	AND EXISTS (
		SELECT 1 FROM memberships m
		WHERE m.user_id = p.created_by AND m.tenant_id = p.tenant_id
	);
