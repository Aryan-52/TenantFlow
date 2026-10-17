-- Global search uses case-insensitive substring matching (lower(col) LIKE '%term%').
-- Trigram GIN indexes make that indexable. pg_trgm ships with PostgreSQL but creating an
-- extension can need extra privileges on some managed databases: if it is not permitted,
-- search still works (sequential scans) and the migration does not fail.
DO $$
BEGIN
	CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION
	WHEN insufficient_privilege OR undefined_file THEN
		RAISE NOTICE 'pg_trgm is not available; search will work without trigram indexes';
END
$$;

DO $$
BEGIN
	IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
		CREATE INDEX IF NOT EXISTS idx_projects_name_trgm ON projects USING gin (lower(name) gin_trgm_ops);
		CREATE INDEX IF NOT EXISTS idx_tasks_title_trgm ON tasks USING gin (lower(title) gin_trgm_ops);
	END IF;
END
$$;

-- Lookups used by search, dashboards and membership checks.
CREATE INDEX IF NOT EXISTS idx_memberships_user ON memberships (user_id);
CREATE INDEX IF NOT EXISTS idx_tasks_project_status ON tasks (project_id, status);
