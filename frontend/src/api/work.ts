import client from './client';

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
  taskCount: number;
  completedTaskCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectInput {
  name: string;
  description: string;
  status: ProjectStatus;
}

export interface Task {
  id: string;
  projectId: string;
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
}

const projectsUrl = (tenantId: string) => `/api/tenants/${tenantId}/projects`;
const tasksUrl = (tenantId: string, projectId: string) => `${projectsUrl(tenantId)}/${projectId}/tasks`;

export const projectApi = {
  list: (tenantId: string) => client.get<Project[]>(projectsUrl(tenantId)).then((r) => r.data),
  get: (tenantId: string, projectId: string) =>
    client.get<Project>(`${projectsUrl(tenantId)}/${projectId}`).then((r) => r.data),
  create: (tenantId: string, input: ProjectInput) =>
    client.post<Project>(projectsUrl(tenantId), input).then((r) => r.data),
  update: (tenantId: string, projectId: string, input: ProjectInput) =>
    client.put<Project>(`${projectsUrl(tenantId)}/${projectId}`, input).then((r) => r.data),
  remove: (tenantId: string, projectId: string) => client.delete(`${projectsUrl(tenantId)}/${projectId}`),
};

export const taskApi = {
  list: (tenantId: string, projectId: string) =>
    client.get<Task[]>(tasksUrl(tenantId, projectId)).then((r) => r.data),
  create: (tenantId: string, projectId: string, input: TaskInput) =>
    client.post<Task>(tasksUrl(tenantId, projectId), input).then((r) => r.data),
  update: (tenantId: string, projectId: string, taskId: string, input: TaskInput) =>
    client.put<Task>(`${tasksUrl(tenantId, projectId)}/${taskId}`, input).then((r) => r.data),
  updateStatus: (tenantId: string, projectId: string, taskId: string, status: TaskStatus) =>
    client.patch<Task>(`${tasksUrl(tenantId, projectId)}/${taskId}/status`, { status }).then((r) => r.data),
  remove: (tenantId: string, projectId: string, taskId: string) =>
    client.delete(`${tasksUrl(tenantId, projectId)}/${taskId}`),
};

/** Extracts the backend's ApiErrorResponse message (and per-field errors) from an axios error. */
export function apiError(err: unknown, fallback: string): { message: string; fieldErrors: Record<string, string> } {
  const data = (err as { response?: { data?: { message?: string; fieldErrors?: Record<string, string> } } })?.response?.data;
  return { message: data?.message || fallback, fieldErrors: data?.fieldErrors || {} };
}
