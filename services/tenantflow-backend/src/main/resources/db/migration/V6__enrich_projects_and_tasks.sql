-- Phase E: richer project and task management.

-- Projects: lifecycle status and creator. Existing rows become ACTIVE with an unknown
-- creator; created_by is nullable so deleting a user never deletes their projects.
ALTER TABLE projects ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE projects ADD CONSTRAINT ck_projects_status
	CHECK (status IN ('ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED'));

ALTER TABLE projects ADD COLUMN created_by UUID;
ALTER TABLE projects ADD CONSTRAINT fk_projects_created_by
	FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX idx_projects_created_by ON projects (created_by);

-- Tasks: creator, optional due date, and the URGENT priority.
ALTER TABLE tasks ADD COLUMN created_by UUID;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_created_by
	FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL;
CREATE INDEX idx_tasks_created_by ON tasks (created_by);

ALTER TABLE tasks ADD COLUMN due_date DATE;
CREATE INDEX idx_tasks_project_due_date ON tasks (project_id, due_date);

ALTER TABLE tasks DROP CONSTRAINT ck_tasks_priority;
ALTER TABLE tasks ADD CONSTRAINT ck_tasks_priority
	CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'));
