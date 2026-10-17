import client from './client';
import type { Page } from './activity';

// Types and calls for the project / task API (see ProjectController and TaskController
// on the backend). Every call is scoped to a tenant; the backend enforces membership.

export type ProjectStatus = 'ACTIVE' | 'ON_HOLD' | 'COMPLETED' | 'ARCHIVED';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';

export interface Project {
  id: string;
  tenantId: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  createdById: string | null;
  createdByName: string | null;
  /** Project owner / lead; null = unassigned. */
  ownerId: string | null;
  ownerName: string | null;
  taskCount: number;
  completedTaskCount: number;
  createdAt: string;
  updatedAt: string;
  /** Optimistic-locking version; send it back on update to avoid overwriting newer edits. */
  version: number;
  /** Planning dates, "YYYY-MM-DD"; optional. */
  startDate?: string | null;
  targetDate?: string | null;
}

export interface ProjectInput {
  name: string;
  description: string;
  status: ProjectStatus;
  /** null = unassigned. Omit to keep the current owner (update) or default to yourself (create). */
  ownerId?: string | null;
  /** Omit to keep (update); null clears. */
  startDate?: string | null;
  targetDate?: string | null;
  /** Update only: reject with 409 if the project changed since this version. */
  version?: number;
}

export interface Task {
  id: string;
  projectId: string;
  projectName: string;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  assigneeId: string | null;
  assigneeName: string | null;
  createdById: string | null;
  createdByName: string | null;
  /** Calendar date, "YYYY-MM-DD", no time zone. */
  dueDate: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
  /** Planned start ("YYYY-MM-DD"); never inferred. */
  startDate?: string | null;
  milestoneId?: string | null;
  recurrence?: Recurrence | null;
  completedAt?: string | null;
  /** Unfinished tasks this one waits on; null/undefined = not computed for this list. */
  openBlockers?: number | null;
}

export type RecurrenceFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY';

export interface Recurrence {
  frequency: RecurrenceFrequency;
  interval: number;
  /** WEEKLY: bit 0 = Monday ... bit 6 = Sunday; 0 = the due date's weekday. */
  weekdays: number;
  until: string | null;
}

/** Sent as one object: omitted = keep (so bulk edits never clear it); present = replace. */
export interface TaskPlanning {
  startDate: string | null;
  milestoneId: string | null;
  recurrence: Recurrence | null;
}

export interface TaskInput {
  title: string;
  description: string;
  status: TaskStatus;
  priority: TaskPriority;
  assigneeId: string | null;
  dueDate: string | null;
  /** Update only: move the task to another project in the same workspace. */
  projectId?: string;
  /** Update only: reject with 409 if the task changed since this version. */
  version?: number;
  planning?: TaskPlanning;
}

/** Cross-project task views (all exclude done tasks); "due-soon" = due today through 7 days ahead. */
export type TaskFocus = 'open' | 'mine' | 'overdue' | 'due-soon';

export const TASK_FOCUS_LABELS: Record<TaskFocus, string> = {
  open: 'Open tasks',
  mine: 'My open tasks',
  overdue: 'Overdue tasks',
  'due-soon': 'Due in the next 7 days',
};

export const isTaskFocus = (value: string | null): value is TaskFocus =>
  value !== null && Object.prototype.hasOwnProperty.call(TASK_FOCUS_LABELS, value);

const projectsUrl = (tenantId: string) => `/api/tenants/${tenantId}/projects`;
const tasksUrl = (tenantId: string, projectId: string) => `${projectsUrl(tenantId)}/${projectId}/tasks`;

export const projectApi = {
  list: (tenantId: string) => client.get<Project[]>(projectsUrl(tenantId)).then((r) => r.data),
  get: (tenantId: string, projectId: string) =>
    client.get<Project>(`${projectsUrl(tenantId)}/${projectId}`).then((r) => r.data),
  /** idempotencyKey: reuse the same value when retrying one logical create. */
  create: (tenantId: string, input: ProjectInput, idempotencyKey?: string) =>
    client
      .post<Project>(projectsUrl(tenantId), input, { headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {} })
      .then((r) => r.data),
  update: (tenantId: string, projectId: string, input: ProjectInput) =>
    client.put<Project>(`${projectsUrl(tenantId)}/${projectId}`, input).then((r) => r.data),
  remove: (tenantId: string, projectId: string) => client.delete(`${projectsUrl(tenantId)}/${projectId}`),
};

export const taskApi = {
  /** Open tasks across every project in the workspace, soonest due first. */
  listForTenant: (tenantId: string, focus: TaskFocus, page = 0, size = 50, signal?: AbortSignal) =>
    client
      .get<Page<Task>>(`/api/tenants/${tenantId}/tasks`, { params: { focus, page, size }, signal })
      .then((r) => r.data),
  list: (tenantId: string, projectId: string) =>
    client.get<Task[]>(tasksUrl(tenantId, projectId)).then((r) => r.data),
  /** idempotencyKey: reuse the same value when retrying one logical create. */
  create: (tenantId: string, projectId: string, input: TaskInput, idempotencyKey?: string) =>
    client
      .post<Task>(tasksUrl(tenantId, projectId), input, { headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {} })
      .then((r) => r.data),
  update: (tenantId: string, projectId: string, taskId: string, input: TaskInput) =>
    client.put<Task>(`${tasksUrl(tenantId, projectId)}/${taskId}`, input).then((r) => r.data),
  updateStatus: (tenantId: string, projectId: string, taskId: string, status: TaskStatus) =>
    client.patch<Task>(`${tasksUrl(tenantId, projectId)}/${taskId}/status`, { status }).then((r) => r.data),
  remove: (tenantId: string, projectId: string, taskId: string) =>
    client.delete(`${tasksUrl(tenantId, projectId)}/${taskId}`),
};

/** A fresh key for one logical create (stable across retries/double-clicks of that create). */
export function newIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`;
}

/** Extracts the backend's ApiErrorResponse message (and per-field errors) from an axios error. */
export function apiError(err: unknown, fallback: string): { message: string; fieldErrors: Record<string, string> } {
  const data = (err as { response?: { data?: { message?: string; fieldErrors?: Record<string, string> } } })?.response?.data;
  return { message: data?.message || fallback, fieldErrors: data?.fieldErrors || {} };
}
