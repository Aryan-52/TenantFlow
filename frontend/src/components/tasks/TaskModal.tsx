import React, { useEffect, useState } from 'react';
import { CalendarX2, Clock, Trash2 } from 'lucide-react';
import { apiError, newIdempotencyKey, taskApi, type Project, type RecurrenceFrequency, type Task, type TaskInput, type TaskPlanning, type TaskPriority, type TaskStatus } from '../../api/work';
import type { Milestone } from '../../api/planning';
import { TaskDependencies } from './TaskDependencies';
import type { Member } from '../../pages/Members';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Select } from '../ui/Select';
import { Button } from '../ui/Button';
import { ErrorState } from '../ui/ErrorState';
import { ConfirmDialog } from '../ui/ConfirmDialog';
import { PRIORITY_OPTIONS, STATUS_OPTIONS } from './taskMeta';
import { AttachmentsPanel } from '../attachments/AttachmentsPanel';
import { TaskComments } from './TaskComments';
import { TaskHistory } from './TaskHistory';
import { Avatar } from '../ui/Avatar';
import { timeAgo } from '../../api/activity';
import { useToast } from '../../contexts/ToastContext';

export interface TaskDefaults {
  status?: TaskStatus;
  dueDate?: string;
}

interface TaskModalProps {
  isOpen: boolean;
  onClose: () => void;
  tenantId: string;
  /** Project the task currently belongs to (or will be created in). */
  projectId: string;
  /** null = create a new task. */
  task: Task | null;
  defaults?: TaskDefaults;
  members: Member[];
  /** Projects the task may be moved to; the selector is hidden when there is only one. */
  projects?: Pick<Project, 'id' | 'name'>[];
  /** Milestones of the task's project; the selector is hidden without any. */
  milestones?: Milestone[];
  canDelete: boolean;
  /** Create mode: let the user pick the project (quick create from the dashboard or a cross-project view). */
  chooseProject?: boolean;
  onSaved: (task: Task) => void;
  onDeleted: (taskId: string) => void;
}

const TITLE_MAX = 255;
const DESCRIPTION_MAX = 2000;

/** Create / view / edit a task. One component so every view shares the same workflow. */
export const TaskModal = ({
  isOpen,
  onClose,
  tenantId,
  projectId,
  task,
  defaults,
  members,
  projects = [],
  milestones = [],
  canDelete,
  chooseProject = false,
  onSaved,
  onDeleted,
}: TaskModalProps) => {
  const toast = useToast();
  const [tab, setTab] = useState<'comments' | 'history' | 'files'>('comments');
  const [commentCount, setCommentCount] = useState<number | null>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState<TaskStatus>('TODO');
  const [priority, setPriority] = useState<TaskPriority>('MEDIUM');
  const [assigneeId, setAssigneeId] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [targetProjectId, setTargetProjectId] = useState(projectId);
  const [startDate, setStartDate] = useState('');
  const [milestoneId, setMilestoneId] = useState('');
  const [repeat, setRepeat] = useState<'' | RecurrenceFrequency>('');
  const [repeatEvery, setRepeatEvery] = useState(1);
  const [repeatDays, setRepeatDays] = useState(0);
  const [repeatUntil, setRepeatUntil] = useState('');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  // One key per opening of the create dialog: double-clicks and retries create one task.
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);

  // Reset the form each time the dialog opens for a (possibly different) task.
  useEffect(() => {
    if (!isOpen) return;
    setTitle(task?.title ?? '');
    setDescription(task?.description ?? '');
    setStatus(task?.status ?? defaults?.status ?? 'TODO');
    setPriority(task?.priority ?? 'MEDIUM');
    setAssigneeId(task?.assigneeId ?? '');
    setDueDate(task?.dueDate ?? defaults?.dueDate ?? '');
    setTargetProjectId(projectId || projects[0]?.id || '');
    setStartDate(task?.startDate ?? '');
    setMilestoneId(task?.milestoneId ?? '');
    setRepeat(task?.recurrence?.frequency ?? '');
    setRepeatEvery(task?.recurrence?.interval ?? 1);
    setRepeatDays(task?.recurrence?.weekdays ?? 0);
    setRepeatUntil(task?.recurrence?.until ?? '');
    setTab('comments');
    setCommentCount(null);
    setError('');
    setFieldErrors({});
    setConfirmDelete(false);
    setConfirmDiscard(false);
    setIdempotencyKey(newIdempotencyKey());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, task?.id]);

  // Unsaved edits: closing asks first, so typing is never lost by a stray Escape or click.
  const dirty = task
    ? title !== task.title ||
      description !== (task.description ?? '') ||
      status !== task.status ||
      priority !== task.priority ||
      assigneeId !== (task.assigneeId ?? '') ||
      dueDate !== (task.dueDate ?? '') ||
      targetProjectId !== projectId ||
      startDate !== (task.startDate ?? '') ||
      milestoneId !== (task.milestoneId ?? '') ||
      repeat !== (task.recurrence?.frequency ?? '') ||
      (repeat !== '' && (repeatEvery !== (task.recurrence?.interval ?? 1) || repeatDays !== (task.recurrence?.weekdays ?? 0) || repeatUntil !== (task.recurrence?.until ?? '')))
    : title.trim() !== '' || description.trim() !== '';
  const requestClose = () => {
    if (saving) return;
    if (dirty) setConfirmDiscard(true);
    else onClose();
  };

  const validate = () => {
    const errs: Record<string, string> = {};
    if (!title.trim()) errs.title = 'Title is required';
    else if (title.trim().length > TITLE_MAX) errs.title = `Title must be at most ${TITLE_MAX} characters`;
    if (description.length > DESCRIPTION_MAX) errs.description = `Description must be at most ${DESCRIPTION_MAX} characters`;
    if (startDate && dueDate && dueDate < startDate) errs.dueDate = 'The due date must be on or after the start date';
    if (repeat && !dueDate) errs.dueDate = 'A repeating task needs a due date';
    if (repeat && repeatUntil && dueDate && repeatUntil < dueDate) errs.repeatUntil = 'The end date must be on or after the due date';
    setFieldErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!validate()) return;
    setSaving(true);
    // The form shows every planning field, so it always sends them (replace semantics).
    const moving = !!task && targetProjectId !== projectId;
    const planning: TaskPlanning = {
      startDate: startDate || null,
      milestoneId: moving ? null : milestoneId || null,
      recurrence: repeat ? { frequency: repeat, interval: repeatEvery, weekdays: repeat === 'WEEKLY' ? repeatDays : 0, until: repeatUntil || null } : null,
    };
    const input: TaskInput = {
      title: title.trim(),
      description,
      status,
      priority,
      assigneeId: assigneeId || null,
      dueDate: dueDate || null,
      planning,
    };
    try {
      const createIn = chooseProject ? targetProjectId : projectId;
      if (!task && !createIn) {
        setFieldErrors({ projectId: 'Choose a project' });
        setSaving(false);
        return;
      }
      const saved = task
        ? await taskApi.update(tenantId, projectId, task.id, { ...input, projectId: targetProjectId, version: task.version })
        : await taskApi.create(tenantId, createIn, input, idempotencyKey);
      onSaved(saved);
      toast.success(task ? 'Task updated' : `Task "${saved.title}" created`);
      onClose();
    } catch (err) {
      const { message, fieldErrors: serverFieldErrors } = apiError(err, 'Failed to save task');
      setError(message);
      setFieldErrors(serverFieldErrors);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    if (!task) return;
    setDeleting(true);
    try {
      await taskApi.remove(tenantId, projectId, task.id);
      setConfirmDelete(false);
      onDeleted(task.id);
      toast.success('Task deleted');
      onClose();
    } catch (err) {
      setConfirmDelete(false);
      setError(apiError(err, 'Failed to delete task').message);
    } finally {
      setDeleting(false);
    }
  };

  // The current assignee may have left the workspace; keep them selectable so saving
  // the form does not silently unassign the task.
  const assigneeOptions = [
    { value: '', label: 'Unassigned' },
    ...members.map((m) => ({ value: m.userId, label: m.name })),
  ];
  if (task?.assigneeId && !members.some((m) => m.userId === task.assigneeId)) {
    assigneeOptions.push({ value: task.assigneeId, label: `${task.assigneeName ?? 'Former member'} (no longer a member)` });
  }

  const projectName = projects.find((p) => p.id === projectId)?.name ?? task?.projectName;

  const titleField = (
    <Input
      id="task-title"
      label="Title"
      value={title}
      onChange={(e) => setTitle(e.target.value)}
      error={fieldErrors.title}
      maxLength={TITLE_MAX}
      required
      placeholder="What needs to be done?"
      autoFocus={!task}
      className={task ? 'task-title-input' : undefined}
    />
  );

  const descriptionField = (
    <div className="form-group">
      <label htmlFor="task-description">Description</label>
      <textarea
        id="task-description"
        value={description}
        onChange={(e) => setDescription(e.target.value)}
        rows={task ? 5 : 3}
        maxLength={DESCRIPTION_MAX}
        className={fieldErrors.description ? 'input-error' : undefined}
        placeholder="Add context, links or acceptance criteria…"
      />
      {fieldErrors.description && <div className="field-error">{fieldErrors.description}</div>}
    </div>
  );

  const dueField = (
    <div>
      <Input
        id="task-due-date"
        type="date"
        label="Due date"
        value={dueDate}
        onChange={(e) => setDueDate(e.target.value)}
        error={fieldErrors.dueDate}
      />
      {dueDate && (
        <button type="button" className="link-button text-xs text-muted task-clear-date" onClick={() => setDueDate('')}>
          <CalendarX2 size={12} aria-hidden="true" /> Remove due date
        </button>
      )}
    </div>
  );

  const WEEKDAY_LABELS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
  const WEEKDAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  const milestoneOptions = [
    { value: '', label: 'No milestone' },
    ...milestones.map((m) => ({ value: m.id, label: `${m.name} · ${m.targetDate}${m.status === 'DONE' ? ' (done)' : ''}` })),
  ];
  const planningFields = (
    <>
      <Input id="task-start-date" type="date" label="Start date" value={startDate} max={dueDate || undefined} onChange={(e) => setStartDate(e.target.value)} hint="Optional. Places the task on the timeline." />
      {milestones.length > 0 && (
        <Select
          id="task-milestone"
          label="Milestone"
          value={targetProjectId !== projectId ? '' : milestoneId}
          disabled={targetProjectId !== projectId}
          onChange={(e) => setMilestoneId(e.target.value)}
          options={milestoneOptions}
          hint={targetProjectId !== projectId ? 'Milestones belong to one project; the link is removed when the task moves.' : undefined}
        />
      )}
      <div className="form-group">
        <label htmlFor="task-repeat">Repeat</label>
        <div className="recurrence-row">
          <select id="task-repeat" value={repeat} onChange={(e) => setRepeat(e.target.value as '' | RecurrenceFrequency)}>
            <option value="">Does not repeat</option>
            <option value="DAILY">Daily</option>
            <option value="WEEKLY">Weekly</option>
            <option value="MONTHLY">Monthly</option>
          </select>
          {repeat && (
            <label className="date-filter">
              every
              <input type="number" min={1} max={365} value={repeatEvery} onChange={(e) => setRepeatEvery(Math.max(1, Math.min(365, Number(e.target.value) || 1)))} aria-label="Repeat interval" style={{ width: '4rem' }} />
              {repeat === 'DAILY' ? 'day(s)' : repeat === 'WEEKLY' ? 'week(s)' : 'month(s)'}
            </label>
          )}
        </div>
        {repeat === 'WEEKLY' && (
          <div className="weekday-toggle mt-2" role="group" aria-label="Repeat on">
            {WEEKDAY_LABELS.map((d, i) => (
              <button key={i} type="button" aria-pressed={(repeatDays & (1 << i)) !== 0} aria-label={WEEKDAY_NAMES[i]} onClick={() => setRepeatDays((v) => v ^ (1 << i))}>
                {d}
              </button>
            ))}
          </div>
        )}
        {repeat && (
          <>
            <label className="date-filter mt-2">
              Ends
              <input type="date" value={repeatUntil} min={dueDate || undefined} onChange={(e) => setRepeatUntil(e.target.value)} aria-label="Repeat until" />
            </label>
            {fieldErrors.repeatUntil && <div className="field-error">{fieldErrors.repeatUntil}</div>}
            <p className="field-hint">
              Completing this task creates the next one, due on the next date. Earlier occurrences are never changed.
              {!dueDate && ' Set a due date first.'}
            </p>
          </>
        )}
      </div>
    </>
  );

  const propertyFields = (
    <>
      <Select id="task-status" label="Status" value={status} onChange={(e) => setStatus(e.target.value as TaskStatus)} options={STATUS_OPTIONS} />
      <Select id="task-priority" label="Priority" value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)} options={PRIORITY_OPTIONS} />
      <Select id="task-assignee" label="Assignee" value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)} options={assigneeOptions} error={fieldErrors.assigneeId} />
      {dueField}
    </>
  );


  const discardDialog = (
    <ConfirmDialog
      isOpen={isOpen && confirmDiscard}
      title="Discard changes?"
      message={task ? 'Your edits to this task have not been saved.' : 'This task has not been created yet.'}
      confirmText="Discard"
      cancelText="Keep editing"
      isDestructive
      onConfirm={() => {
        setConfirmDiscard(false);
        onClose();
      }}
      onCancel={() => setConfirmDiscard(false)}
    />
  );

  // ---- Create: a focused dialog ----
  if (!task) {
    return (
      <>
        <Modal isOpen={isOpen && !confirmDelete && !confirmDiscard} onClose={requestClose} title="Create task" size="lg">
          <form id="task-modal-form" onSubmit={handleSubmit} noValidate>
            <ErrorState message={error} />
            {chooseProject && (
              <Select
                id="task-project"
                label="Project"
                value={targetProjectId}
                onChange={(e) => setTargetProjectId(e.target.value)}
                options={[{ value: '', label: 'Choose a project…' }, ...projects.map((p) => ({ value: p.id, label: p.name }))]}
                error={fieldErrors.projectId}
                required
              />
            )}
            {titleField}
            {descriptionField}
            <div className="form-row">{propertyFields}</div>
            <details className="more-fields">
              <summary>Planning: start date, milestone, repeat</summary>
              {planningFields}
            </details>
          </form>
          <div className="modal-actions">
            <Button variant="secondary" onClick={requestClose} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="task-modal-form" isLoading={saving}>
              Create task
            </Button>
          </div>
        </Modal>
        {discardDialog}
      </>
    );
  }

  // ---- Existing task: a side panel with the full context ----
  return (
    <>
      <Modal
        isOpen={isOpen && !confirmDelete && !confirmDiscard}
        onClose={requestClose}
        title="Task details"
        variant="drawer"
        eyebrow={projectName}
        footer={
          <>
            {canDelete && (
              <Button variant="ghost" className="text-danger mr-auto" onClick={() => setConfirmDelete(true)} disabled={saving}>
                <Trash2 size={15} /> Delete
              </Button>
            )}
            <Button variant="secondary" onClick={requestClose} disabled={saving}>
              Close
            </Button>
            <Button type="submit" form="task-modal-form" isLoading={saving}>
              Save changes
            </Button>
          </>
        }
      >
        <form id="task-modal-form" onSubmit={handleSubmit} noValidate className="task-panel">
          <div className="task-panel-main">
            <ErrorState message={error} />
            {titleField}
            {descriptionField}
          </div>

          <aside className="task-panel-props" aria-label="Task properties">
            {propertyFields}
            {planningFields}
            <TaskDependencies tenantId={tenantId} taskId={task.id} />
            {projects.length > 1 && (
              <Select
                id="task-project"
                label="Project"
                value={targetProjectId}
                onChange={(e) => setTargetProjectId(e.target.value)}
                options={projects.map((p) => ({ value: p.id, label: p.name }))}
                hint={targetProjectId !== projectId ? 'The task moves to this project when you save.' : undefined}
              />
            )}
            <dl className="task-meta" aria-label="Record">
              <div>
                <dt>Created</dt>
                <dd title={new Date(task.createdAt).toLocaleString()}>
                  {task.createdByName ? (
                    <span className="assignee">
                      <Avatar name={task.createdByName} size="xs" /> {task.createdByName}
                    </span>
                  ) : (
                    'Unknown'
                  )}
                  <span className="text-faint"> · {timeAgo(task.createdAt)}</span>
                </dd>
              </div>
              <div>
                <dt>Updated</dt>
                <dd title={new Date(task.updatedAt).toLocaleString()}>
                  <Clock size={12} aria-hidden="true" /> {timeAgo(task.updatedAt)}
                </dd>
              </div>
            </dl>
          </aside>
        </form>

        <section className="task-panel-tabs" aria-label="Discussion, history and files">
          <div className="segmented" role="tablist" aria-label="Task sections">
            <button type="button" role="tab" aria-selected={tab === 'comments'} className={tab === 'comments' ? 'active' : ''} onClick={() => setTab('comments')}>
              Comments{commentCount ? ` · ${commentCount}` : ''}
            </button>
            <button type="button" role="tab" aria-selected={tab === 'history'} className={tab === 'history' ? 'active' : ''} onClick={() => setTab('history')}>
              History
            </button>
            <button type="button" role="tab" aria-selected={tab === 'files'} className={tab === 'files' ? 'active' : ''} onClick={() => setTab('files')}>
              Files
            </button>
          </div>
          <div className="task-panel-tab" role="tabpanel">
            {tab === 'comments' && (
              <TaskComments tenantId={tenantId} projectId={projectId} taskId={task.id} canModerate={canDelete} onCountChange={setCommentCount} members={members} />
            )}
            {tab === 'history' && <TaskHistory tenantId={tenantId} taskId={task.id} />}
            {tab === 'files' && <AttachmentsPanel tenantId={tenantId} projectId={projectId} taskId={task.id} canManage={canDelete} />}
          </div>
        </section>
      </Modal>

      <ConfirmDialog
        isOpen={isOpen && confirmDelete}
        title="Delete task"
        message={`Delete "${task?.title ?? ''}"? This action cannot be undone.`}
        confirmText="Delete task"
        isDestructive
        isLoading={deleting}
        onConfirm={handleDelete}
        onCancel={() => setConfirmDelete(false)}
      />
      {discardDialog}
    </>
  );
};
