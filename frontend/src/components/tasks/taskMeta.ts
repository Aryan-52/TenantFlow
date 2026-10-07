import type { ProjectStatus, Task, TaskPriority, TaskStatus } from '../../api/work';

export const TASK_STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE'];

export const STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'To Do',
  IN_PROGRESS: 'In Progress',
  DONE: 'Done',
};

export const PRIORITY_LABELS: Record<TaskPriority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  URGENT: 'Urgent',
};

/** Higher = more important; used for sorting. */
export const PRIORITY_RANK: Record<TaskPriority, number> = { LOW: 0, MEDIUM: 1, HIGH: 2, URGENT: 3 };

export const PROJECT_STATUS_LABELS: Record<ProjectStatus, string> = {
  ACTIVE: 'Active',
  ON_HOLD: 'On hold',
  COMPLETED: 'Completed',
  ARCHIVED: 'Archived',
};

export const STATUS_OPTIONS = TASK_STATUSES.map((s) => ({ value: s, label: STATUS_LABELS[s] }));
export const PRIORITY_OPTIONS = (Object.keys(PRIORITY_LABELS) as TaskPriority[]).map((p) => ({
  value: p,
  label: PRIORITY_LABELS[p],
}));
export const PROJECT_STATUS_OPTIONS = (Object.keys(PROJECT_STATUS_LABELS) as ProjectStatus[]).map((s) => ({
  value: s,
  label: PROJECT_STATUS_LABELS[s],
}));

// ---- Due dates -------------------------------------------------------------
// Due dates are plain calendar dates ("YYYY-MM-DD"). They are always handled as local
// dates here - never via `new Date("YYYY-MM-DD")`, which parses as UTC midnight and can
// shift the day for users west of UTC.

const pad = (n: number) => String(n).padStart(2, '0');

export const toDateKey = (d: Date) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;

export const parseDateKey = (key: string) => {
  const [y, m, d] = key.split('-').map(Number);
  return new Date(y, m - 1, d);
};

export const todayKey = () => toDateKey(new Date());

export const addDays = (d: Date, n: number) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);

export const formatDueDate = (key: string) =>
  parseDateKey(key).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });

export type DueState = 'none' | 'done' | 'overdue' | 'today' | 'upcoming';

export const dueState = (task: Pick<Task, 'dueDate' | 'status'>, today = todayKey()): DueState => {
  if (!task.dueDate) return 'none';
  if (task.status === 'DONE') return 'done';
  // "YYYY-MM-DD" strings compare correctly as plain strings.
  if (task.dueDate < today) return 'overdue';
  if (task.dueDate === today) return 'today';
  return 'upcoming';
};

/** Sort: open tasks with the nearest due date first, then by priority. */
export const compareByDueThenPriority = (a: Task, b: Task) => {
  if ((a.status === 'DONE') !== (b.status === 'DONE')) return a.status === 'DONE' ? 1 : -1;
  if (a.dueDate && b.dueDate && a.dueDate !== b.dueDate) return a.dueDate < b.dueDate ? -1 : 1;
  if (a.dueDate && !b.dueDate) return -1;
  if (!a.dueDate && b.dueDate) return 1;
  return PRIORITY_RANK[b.priority] - PRIORITY_RANK[a.priority];
};

/** Open task that is overdue or due within the next `days` days. */
export const isDueWithinDays = (task: Pick<Task, 'dueDate' | 'status'>, days: number, today = todayKey()) => {
  if (!task.dueDate || task.status === 'DONE') return false;
  return task.dueDate <= toDateKey(addDays(parseDateKey(today), days));
};
