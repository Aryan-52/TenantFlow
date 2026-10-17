-- TenantFlow 2.0: planning (dates, milestones, dependencies, recurrence), personal focus,
-- capacity, notification preferences and workspace automations.
-- Additive only: new nullable columns and new tables. No existing row is modified and no
-- new column is required, so existing projects and tasks keep working unchanged.

-- ---- Project planning dates -------------------------------------------------------------
ALTER TABLE projects ADD COLUMN start_date DATE;
ALTER TABLE projects ADD COLUMN target_date DATE;
ALTER TABLE projects ADD CONSTRAINT ck_projects_dates
	CHECK (start_date IS NULL OR target_date IS NULL OR target_date >= start_date);

-- ---- Milestones ---------------------------------------------------------------------------
CREATE TABLE project_milestones (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	tenant_id UUID NOT NULL,
	project_id UUID NOT NULL,
	name VARCHAR(160) NOT NULL,
	description VARCHAR(2000),
	target_date DATE NOT NULL,
	status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
	completed_at TIMESTAMPTZ,
	created_by UUID,
	created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	version BIGINT NOT NULL DEFAULT 0,
	CONSTRAINT ck_milestones_status CHECK (status IN ('OPEN', 'DONE')),
	CONSTRAINT ck_milestones_name CHECK (length(btrim(name)) > 0),
	CONSTRAINT fk_milestones_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
	CONSTRAINT fk_milestones_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
	CONSTRAINT fk_milestones_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_milestones_project_date ON project_milestones (project_id, target_date);
CREATE INDEX idx_milestones_tenant_date ON project_milestones (tenant_id, target_date);

-- ---- Task planning fields -----------------------------------------------------------------
-- start_date: never inferred for existing tasks (stays NULL until someone sets it).
ALTER TABLE tasks ADD COLUMN start_date DATE;
ALTER TABLE tasks ADD COLUMN milestone_id UUID;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_milestone
	FOREIGN KEY (milestone_id) REFERENCES project_milestones (id) ON DELETE SET NULL;
ALTER TABLE tasks ADD CONSTRAINT ck_tasks_dates
	CHECK (start_date IS NULL OR due_date IS NULL OR due_date >= start_date);
CREATE INDEX idx_tasks_milestone ON tasks (milestone_id) WHERE milestone_id IS NOT NULL;

-- Recurrence: each occurrence carries the rule; completing an occurrence creates the next one.
-- (series_id, due_date) is unique, so the next occurrence can never be created twice.
ALTER TABLE tasks ADD COLUMN recurrence_frequency VARCHAR(16);
ALTER TABLE tasks ADD COLUMN recurrence_interval INTEGER;
ALTER TABLE tasks ADD COLUMN recurrence_weekdays SMALLINT;
ALTER TABLE tasks ADD COLUMN recurrence_until DATE;
ALTER TABLE tasks ADD COLUMN series_id UUID;
ALTER TABLE tasks ADD CONSTRAINT ck_tasks_recurrence CHECK (
	recurrence_frequency IS NULL
	OR (recurrence_frequency IN ('DAILY', 'WEEKLY', 'MONTHLY')
		AND recurrence_interval BETWEEN 1 AND 365
		AND series_id IS NOT NULL
		AND due_date IS NOT NULL));
CREATE UNIQUE INDEX uk_tasks_series_due ON tasks (series_id, due_date) WHERE series_id IS NOT NULL;

-- ---- Dependencies: blocked_task cannot be finished until blocking_task is done -------------
CREATE TABLE task_dependencies (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	tenant_id UUID NOT NULL,
	blocking_task_id UUID NOT NULL,
	blocked_task_id UUID NOT NULL,
	created_by UUID,
	created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT uk_task_dependencies UNIQUE (blocking_task_id, blocked_task_id),
	CONSTRAINT ck_task_dependencies_self CHECK (blocking_task_id <> blocked_task_id),
	CONSTRAINT fk_task_dependencies_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
	CONSTRAINT fk_task_dependencies_blocking FOREIGN KEY (blocking_task_id) REFERENCES tasks (id) ON DELETE CASCADE,
	CONSTRAINT fk_task_dependencies_blocked FOREIGN KEY (blocked_task_id) REFERENCES tasks (id) ON DELETE CASCADE,
	CONSTRAINT fk_task_dependencies_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
);
CREATE INDEX idx_task_dependencies_blocked ON task_dependencies (blocked_task_id);
CREATE INDEX idx_task_dependencies_tenant ON task_dependencies (tenant_id);

-- ---- Personal "today" list: tasks a user chose to focus on for a given day ---------------
CREATE TABLE task_focus (
	user_id UUID NOT NULL,
	task_id UUID NOT NULL,
	focus_date DATE NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT pk_task_focus PRIMARY KEY (user_id, task_id),
	CONSTRAINT fk_task_focus_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
	CONSTRAINT fk_task_focus_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE
);

-- ---- Capacity: open tasks a member can take per week (explicit unit: tasks/week) -------
ALTER TABLE memberships ADD COLUMN weekly_capacity INTEGER;
ALTER TABLE memberships ADD CONSTRAINT ck_memberships_capacity
	CHECK (weekly_capacity IS NULL OR weekly_capacity BETWEEN 0 AND 200);

-- ---- Notification preferences (no row = defaults) ----------------------------------------
CREATE TABLE notification_preferences (
	user_id UUID PRIMARY KEY,
	due_soon_enabled BOOLEAN NOT NULL DEFAULT TRUE,
	due_soon_days INTEGER NOT NULL DEFAULT 1,
	overdue_enabled BOOLEAN NOT NULL DEFAULT TRUE,
	mentions_enabled BOOLEAN NOT NULL DEFAULT TRUE,
	time_zone VARCHAR(64),
	updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT ck_notification_preferences_days CHECK (due_soon_days BETWEEN 0 AND 14),
	CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- ---- Workspace automations: fixed rule types, switched on per workspace --------------------
CREATE TABLE automation_rules (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	tenant_id UUID NOT NULL,
	rule_type VARCHAR(64) NOT NULL,
	enabled BOOLEAN NOT NULL DEFAULT FALSE,
	updated_by UUID,
	updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT uk_automation_rules UNIQUE (tenant_id, rule_type),
	CONSTRAINT fk_automation_rules_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE,
	CONSTRAINT fk_automation_rules_user FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
);

-- Execution history; dedupe_key makes each trigger run at most once.
CREATE TABLE automation_runs (
	id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
	tenant_id UUID NOT NULL,
	rule_type VARCHAR(64) NOT NULL,
	task_id UUID,
	outcome VARCHAR(16) NOT NULL,
	message VARCHAR(500) NOT NULL,
	dedupe_key VARCHAR(200) NOT NULL,
	created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
	CONSTRAINT ck_automation_runs_outcome CHECK (outcome IN ('SUCCESS', 'SKIPPED', 'FAILED')),
	CONSTRAINT uk_automation_runs_dedupe UNIQUE (tenant_id, dedupe_key),
	CONSTRAINT fk_automation_runs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants (id) ON DELETE CASCADE
);
CREATE INDEX idx_automation_runs_tenant_time ON automation_runs (tenant_id, created_at DESC);

-- Due-date queries for reminders and the planning calendar.
CREATE INDEX idx_tasks_assignee_due ON tasks (assignee_id, due_date) WHERE status <> 'DONE';
