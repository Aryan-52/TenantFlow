-- When a task was completed, for delivery analytics (completion trend, on-time rate,
-- cycle time). Additive: one nullable column; no existing data is changed except the
-- backfill below, which only uses events the application actually recorded.
ALTER TABLE tasks ADD COLUMN completed_at TIMESTAMPTZ;

-- Backfill 1: done tasks whose move to Done was recorded in the activity log (V10+):
-- the most recent such event is when they were (last) completed.
UPDATE tasks t
SET completed_at = e.completed_at
FROM (
	SELECT a.task_id, MAX(a.created_at) AS completed_at
	FROM activity_events a
	WHERE a.type = 'TASK_STATUS_CHANGED' AND a.summary LIKE '% to Done'
	GROUP BY a.task_id
) e
WHERE t.id = e.task_id AND t.status = 'DONE';

-- Backfill 2: tasks created as Done and never modified afterwards were completed at creation.
UPDATE tasks
SET completed_at = created_at
WHERE status = 'DONE' AND completed_at IS NULL AND updated_at = created_at;

-- Everything else that is done keeps NULL: its completion time was never recorded.
CREATE INDEX idx_tasks_completed_at ON tasks (completed_at) WHERE completed_at IS NOT NULL;
