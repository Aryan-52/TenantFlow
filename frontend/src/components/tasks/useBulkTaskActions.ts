import { useState, type Dispatch, type SetStateAction } from 'react';
import { apiError, taskApi, type Task, type TaskPriority, type TaskStatus } from '../../api/work';
import { useToast } from '../../contexts/ToastContext';

/**
 * Apply one change to several tasks through the existing task endpoints (one request per
 * task, so each keeps its own permission checks and optimistic locking). Successful tasks
 * update in place; failures are reported together and leave those tasks unchanged.
 */
export function useBulkTaskActions(tenantId: string | undefined, setTasks: Dispatch<SetStateAction<Task[]>>) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  const run = async (tasks: Task[], action: (t: Task) => Promise<Task | null>, done: string) => {
    if (!tenantId || tasks.length === 0) return;
    setBusy(true);
    const results = await Promise.allSettled(tasks.map(action));
    const saved = new Map<string, Task | null>();
    let firstError = '';
    results.forEach((r, i) => {
      if (r.status === 'fulfilled') saved.set(tasks[i].id, r.value);
      else if (!firstError) firstError = apiError(r.reason, 'Some tasks could not be updated').message;
    });
    setTasks((prev) =>
      prev.flatMap((t) => {
        if (!saved.has(t.id)) return [t];
        const next = saved.get(t.id);
        return next ? [next] : []; // null = deleted
      }),
    );
    const failed = tasks.length - saved.size;
    if (saved.size > 0) toast.success(`${done} ${saved.size} task${saved.size === 1 ? '' : 's'}`);
    if (failed > 0) toast.error(`${failed} task${failed === 1 ? '' : 's'} not changed: ${firstError}`);
    setBusy(false);
  };

  const setStatus = (tasks: Task[], status: TaskStatus) =>
    run(
      tasks.filter((t) => t.status !== status),
      (t) => taskApi.updateStatus(tenantId!, t.projectId, t.id, status),
      'Updated',
    );

  /** Full update with one field changed (keeps every other field and the version check). */
  const updateWith = (t: Task, patch: Partial<Pick<Task, 'assigneeId' | 'priority' | 'dueDate'>>) =>
    taskApi.update(tenantId!, t.projectId, t.id, {
      title: t.title,
      description: t.description ?? '',
      status: t.status,
      priority: patch.priority ?? t.priority,
      assigneeId: 'assigneeId' in patch ? patch.assigneeId ?? null : t.assigneeId,
      dueDate: 'dueDate' in patch ? patch.dueDate ?? null : t.dueDate,
      version: t.version,
    });

  const assign = (tasks: Task[], assigneeId: string | null) =>
    run(tasks.filter((t) => (t.assigneeId ?? null) !== assigneeId), (t) => updateWith(t, { assigneeId }), 'Reassigned');

  const setPriority = (tasks: Task[], priority: TaskPriority) =>
    run(tasks.filter((t) => t.priority !== priority), (t) => updateWith(t, { priority }), 'Updated');

  const setDueDate = (tasks: Task[], dueDate: string | null) =>
    run(tasks.filter((t) => (t.dueDate ?? null) !== dueDate), (t) => updateWith(t, { dueDate }), dueDate ? 'Rescheduled' : 'Cleared the due date of');

  const remove = (tasks: Task[]) =>
    run(tasks, (t) => taskApi.remove(tenantId!, t.projectId, t.id).then(() => null), 'Deleted');

  return { busy, setStatus, assign, setPriority, setDueDate, remove };
}
