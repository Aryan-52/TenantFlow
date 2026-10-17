import { useState } from 'react';
import { rowKeyNav, taskHref } from './taskLinks';
import { Link } from 'react-router-dom';
import { Ban, Pin, PinOff, Repeat } from 'lucide-react';
import { apiError, taskApi, type Task, type TaskPriority, type TaskStatus } from '../../api/work';
import { useToast } from '../../contexts/ToastContext';
import type { Member } from '../../pages/Members';
import { PRIORITY_OPTIONS, STATUS_OPTIONS } from './taskMeta';
import { DueDateLabel } from './DueDateLabel';

interface TaskRowProps {
  tenantId: string;
  task: Task;
  onChange: (task: Task) => void;
  /** Members for reassignment; omit to hide the assignee control. */
  members?: Member[];
  showProject?: boolean;
  pinned?: boolean;
  onTogglePin?: (task: Task) => void;
}

/**
 * One task with its context (project, due date, blockers, recurrence) and inline edits.
 * Status uses the narrow status endpoint; other edits send the full task with its version,
 * so a concurrent change is reported instead of overwritten.
 */
export const TaskRow = ({ tenantId, task, onChange, members, showProject = true, pinned, onTogglePin }: TaskRowProps) => {
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  const run = async (fn: () => Promise<Task>, done?: string) => {
    setBusy(true);
    try {
      onChange(await fn());
      if (done) toast.success(done);
    } catch (err) {
      toast.error(apiError(err, 'The task could not be updated').message);
    } finally {
      setBusy(false);
    }
  };

  const update = (patch: Partial<Pick<Task, 'priority' | 'dueDate' | 'assigneeId'>>) =>
    run(() =>
      taskApi.update(tenantId, task.projectId, task.id, {
        title: task.title,
        description: task.description ?? '',
        status: task.status,
        priority: patch.priority ?? task.priority,
        assigneeId: 'assigneeId' in patch ? patch.assigneeId ?? null : task.assigneeId,
        dueDate: 'dueDate' in patch ? patch.dueDate ?? null : task.dueDate,
        version: task.version,
      }),
    );

  const blocked = (task.openBlockers ?? 0) > 0;

  return (
    <li className={`work-row ${blocked ? 'is-blocked' : ''} ${task.status === 'DONE' ? 'is-done' : ''}`.trim()}>
      <span className={`status-dot is-${task.status.toLowerCase()}`} aria-hidden="true" />
      <div className="work-row-main">
        <Link to={taskHref(tenantId, task)} className="work-row-title" data-task-row-link onKeyDown={rowKeyNav}>
          {task.title}
        </Link>
        <div className="work-row-meta">
          {showProject && <span className="truncate">{task.projectName}</span>}
          {blocked && (
            <span className="meta-chip is-danger" title="Waits on unfinished tasks; open the task to see which">
              <Ban size={11} aria-hidden="true" /> Blocked by {task.openBlockers}
            </span>
          )}
          {task.recurrence && (
            <span className="meta-chip" title="Recurring task">
              <Repeat size={11} aria-hidden="true" /> Repeats
            </span>
          )}
          <DueDateLabel task={task} />
        </div>
      </div>
      <div className="work-row-controls">
        <select
          className="mini-select"
          aria-label={`Status of ${task.title}`}
          value={task.status}
          disabled={busy}
          onChange={(e) => run(() => taskApi.updateStatus(tenantId, task.projectId, task.id, e.target.value as TaskStatus))}
        >
          {STATUS_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>
        <select
          className="mini-select"
          aria-label={`Priority of ${task.title}`}
          value={task.priority}
          disabled={busy}
          onChange={(e) => update({ priority: e.target.value as TaskPriority })}
        >
          {PRIORITY_OPTIONS.map((o) => (
            <option key={o.value} value={o.value}>{o.label}</option>
          ))}
        </select>
        <input
          type="date"
          className="mini-date"
          aria-label={`Due date of ${task.title}`}
          value={task.dueDate ?? ''}
          disabled={busy}
          onChange={(e) => update({ dueDate: e.target.value || null })}
        />
        {members && (
          <select
            className="mini-select"
            aria-label={`Assignee of ${task.title}`}
            value={task.assigneeId ?? ''}
            disabled={busy}
            onChange={(e) => update({ assigneeId: e.target.value || null }).then(() => undefined)}
          >
            <option value="">Unassigned</option>
            {task.assigneeId && !members.some((m) => m.userId === task.assigneeId) && (
              <option value={task.assigneeId}>{task.assigneeName ?? 'Former member'}</option>
            )}
            {members.map((m) => (
              <option key={m.userId} value={m.userId}>{m.name}</option>
            ))}
          </select>
        )}
        {onTogglePin && (
          <button
            type="button"
            className={`icon-button ${pinned ? 'is-on' : ''}`.trim()}
            aria-pressed={!!pinned}
            aria-label={pinned ? `Remove ${task.title} from today` : `Add ${task.title} to today`}
            data-tooltip={pinned ? 'Remove from Today' : 'Add to Today'}
            onClick={() => onTogglePin(task)}
          >
            {pinned ? <PinOff size={15} /> : <Pin size={15} />}
          </button>
        )}
      </div>
    </li>
  );
};
