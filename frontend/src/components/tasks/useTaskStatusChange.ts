import { useState, type Dispatch, type SetStateAction } from 'react';
import { apiError, taskApi, type Task, type TaskStatus } from '../../api/work';
import { STATUS_LABELS } from './taskMeta';

/**
 * Optimistic task status change (Kanban moves, table status selects): the task moves in
 * the UI immediately, is persisted through PATCH .../status, and is restored to its
 * previous status if the server rejects the change.
 */
export function useTaskStatusChange(tenantId: string | undefined, setTasks: Dispatch<SetStateAction<Task[]>>) {
  const [pendingIds, setPendingIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState('');

  const changeStatus = async (task: Task, status: TaskStatus) => {
    if (!tenantId || task.status === status || pendingIds.has(task.id)) return;
    setError('');
    setPendingIds((prev) => new Set(prev).add(task.id));
    setTasks((prev) => prev.map((t) => (t.id === task.id ? { ...t, status } : t)));
    try {
      const saved = await taskApi.updateStatus(tenantId, task.projectId, task.id, status);
      setTasks((prev) => prev.map((t) => (t.id === saved.id ? saved : t)));
    } catch (err) {
      setTasks((prev) => prev.map((t) => (t.id === task.id ? { ...t, status: task.status } : t)));
      setError(apiError(err, `Couldn't move "${task.title}" to ${STATUS_LABELS[status]}`).message);
    } finally {
      setPendingIds((prev) => {
        const next = new Set(prev);
        next.delete(task.id);
        return next;
      });
    }
  };

  return { pendingIds, changeStatus, error, clearError: () => setError('') };
}
