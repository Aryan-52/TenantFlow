import React, { useEffect, useState } from 'react';
import { Trash2 } from 'lucide-react';
import { apiError, taskApi, type Project, type Task, type TaskInput, type TaskPriority, type TaskStatus } from '../../api/work';
import type { Member } from '../../pages/Members';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Select } from '../ui/Select';
import { Button } from '../ui/Button';
import { ErrorState } from '../ui/ErrorState';
import { ConfirmDialog } from '../ui/ConfirmDialog';
import { PRIORITY_OPTIONS, STATUS_OPTIONS } from './taskMeta';

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
  canDelete: boolean;
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
  canDelete,
  onSaved,
  onDeleted,
}: TaskModalProps) => {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState<TaskStatus>('TODO');
  const [priority, setPriority] = useState<TaskPriority>('MEDIUM');
  const [assigneeId, setAssigneeId] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [targetProjectId, setTargetProjectId] = useState(projectId);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // Reset the form each time the dialog opens for a (possibly different) task.
  useEffect(() => {
    if (!isOpen) return;
    setTitle(task?.title ?? '');
    setDescription(task?.description ?? '');
    setStatus(task?.status ?? defaults?.status ?? 'TODO');
    setPriority(task?.priority ?? 'MEDIUM');
    setAssigneeId(task?.assigneeId ?? '');
    setDueDate(task?.dueDate ?? defaults?.dueDate ?? '');
    setTargetProjectId(projectId);
    setError('');
    setFieldErrors({});
    setConfirmDelete(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, task?.id]);

  const validate = () => {
    const errs: Record<string, string> = {};
    if (!title.trim()) errs.title = 'Title is required';
    else if (title.trim().length > TITLE_MAX) errs.title = `Title must be at most ${TITLE_MAX} characters`;
    if (description.length > DESCRIPTION_MAX) errs.description = `Description must be at most ${DESCRIPTION_MAX} characters`;
    setFieldErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!validate()) return;
    setSaving(true);
    const input: TaskInput = {
      title: title.trim(),
      description,
      status,
      priority,
      assigneeId: assigneeId || null,
      dueDate: dueDate || null,
    };
    try {
      const saved = task
        ? await taskApi.update(tenantId, projectId, task.id, { ...input, projectId: targetProjectId })
        : await taskApi.create(tenantId, projectId, input);
      onSaved(saved);
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

  return (
    <>
      <Modal isOpen={isOpen && !confirmDelete} onClose={onClose} title={task ? 'Task details' : 'Create task'} size="lg">
        <form id="task-modal-form" onSubmit={handleSubmit} noValidate>
          <ErrorState message={error} />
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
          />
          <div className="form-group">
            <label htmlFor="task-description">Description</label>
            <textarea
              id="task-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={4}
              maxLength={DESCRIPTION_MAX}
              className={fieldErrors.description ? 'input-error' : undefined}
              placeholder="Add more details..."
            />
            {fieldErrors.description && <div className="field-error">{fieldErrors.description}</div>}
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Select id="task-status" label="Status" value={status} onChange={(e) => setStatus(e.target.value as TaskStatus)} options={STATUS_OPTIONS} />
            <Select id="task-priority" label="Priority" value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)} options={PRIORITY_OPTIONS} />
            <Select id="task-assignee" label="Assignee" value={assigneeId} onChange={(e) => setAssigneeId(e.target.value)} options={assigneeOptions} error={fieldErrors.assigneeId} />
            <Input
              id="task-due-date"
              type="date"
              label="Due date"
              value={dueDate}
              onChange={(e) => setDueDate(e.target.value)}
              error={fieldErrors.dueDate}
              hint={dueDate ? undefined : 'Optional'}
            />
            {task && projects.length > 1 && (
              <div className="md:col-span-2">
                <Select
                  id="task-project"
                  label="Project"
                  value={targetProjectId}
                  onChange={(e) => setTargetProjectId(e.target.value)}
                  options={projects.map((p) => ({ value: p.id, label: p.name }))}
                  hint={targetProjectId !== projectId ? 'The task will be moved to this project when you save.' : undefined}
                />
              </div>
            )}
          </div>
          {dueDate && (
            <button type="button" className="btn btn-ghost btn-sm mb-4" onClick={() => setDueDate('')}>
              Remove due date
            </button>
          )}
          {task && (
            <p className="text-xs text-muted mb-4">
              Created {new Date(task.createdAt).toLocaleString()}
              {task.createdByName ? ` by ${task.createdByName}` : ''} · Updated {new Date(task.updatedAt).toLocaleString()}
            </p>
          )}
        </form>
        <div className="flex justify-between items-center gap-2">
          <div>
            {task && canDelete && (
              <Button variant="danger-outline" onClick={() => setConfirmDelete(true)} disabled={saving}>
                <Trash2 size={15} /> Delete
              </Button>
            )}
          </div>
          <div className="flex gap-2">
            <Button variant="secondary" onClick={onClose} disabled={saving}>
              Cancel
            </Button>
            <Button type="submit" form="task-modal-form" isLoading={saving}>
              {task ? 'Save changes' : 'Create task'}
            </Button>
          </div>
        </div>
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
    </>
  );
};
