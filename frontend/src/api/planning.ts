import client from './client';
import type { Page } from './activity';
import type { ProjectStatus, Task, TaskStatus } from './work';
import type { Signal } from './analytics';

// TenantFlow 2.0 planning API: milestones, dependencies, My Work, calendar, portfolio and
// team workload. Every call is scoped to a workspace; the backend checks membership.

export type MilestoneStatus = 'OPEN' | 'DONE';

export interface Milestone {
  id: string;
  projectId: string;
  projectName: string | null;
  name: string;
  description: string | null;
  targetDate: string;
  status: MilestoneStatus;
  completedAt: string | null;
  totalTasks: number;
  doneTasks: number;
  version: number;
}

export interface MilestoneInput {
  name: string;
  description: string;
  targetDate: string;
  status?: MilestoneStatus;
  version?: number;
}

export interface TaskRef {
  dependencyId: string;
  id: string;
  projectId: string;
  projectName: string;
  title: string;
  status: TaskStatus;
  dueDate: string | null;
  assigneeName: string | null;
}

export interface Dependencies {
  blockedBy: TaskRef[];
  blocks: TaskRef[];
  openBlockers: number;
}

export interface TaskLookup {
  id: string;
  projectId: string;
  projectName: string;
  title: string;
  status: TaskStatus;
}

export interface MyWork {
  today: string;
  open: Task[];
  focusTaskIds: string[];
  completed: Task[];
  completedDays: number;
}

export type CalendarScope = 'mine' | 'workspace';

export interface CalendarData {
  from: string;
  to: string;
  scope: string;
  tasks: Task[];
  milestones: Milestone[];
  truncated: boolean;
}

export interface PortfolioRow {
  id: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  ownerId: string | null;
  ownerName: string | null;
  startDate: string | null;
  targetDate: string | null;
  totalTasks: number;
  doneTasks: number;
  openTasks: number;
  overdueTasks: number;
  blockedTasks: number;
  milestonesOpen: number;
  milestonesDone: number;
  nextMilestone: { id: string; name: string; targetDate: string } | null;
  lastActivityAt: string;
  signals: Signal[];
}

export interface WeekBucket {
  weekStart: string;
  due: number;
}

export interface ProjectCount {
  projectId: string;
  projectName: string;
  open: number;
}

export type Role = 'OWNER' | 'ADMIN' | 'MEMBER';

export interface MemberLoad {
  userId: string | null;
  name: string | null;
  email: string | null;
  role: Role | null;
  /** Tasks per week; null = not set. */
  weeklyCapacity: number | null;
  open: number;
  todo: number;
  inProgress: number;
  overdue: number;
  blocked: number;
  noDueDate: number;
  weeks: WeekBucket[];
  later: number;
  projects: ProjectCount[];
  warning: string | null;
}

export interface Workload {
  today: string;
  weeks: number;
  members: MemberLoad[];
  unassigned: MemberLoad;
}

export interface MemberOverview {
  userId: string;
  name: string;
  email: string;
  role: Role;
  joinedAt: string;
  weeklyCapacity: number | null;
  open: Task[];
  completedRecently: Task[];
  projects: ProjectCount[];
}

const base = (tenantId: string) => `/api/tenants/${tenantId}`;

export const milestoneApi = {
  list: (tenantId: string, projectId: string, signal?: AbortSignal) =>
    client.get<Milestone[]>(`${base(tenantId)}/projects/${projectId}/milestones`, { signal }).then((r) => r.data),
  listAll: (tenantId: string, signal?: AbortSignal) =>
    client.get<Milestone[]>(`${base(tenantId)}/milestones`, { signal }).then((r) => r.data),
  create: (tenantId: string, projectId: string, input: MilestoneInput) =>
    client.post<Milestone>(`${base(tenantId)}/projects/${projectId}/milestones`, input).then((r) => r.data),
  update: (tenantId: string, projectId: string, id: string, input: MilestoneInput) =>
    client.put<Milestone>(`${base(tenantId)}/projects/${projectId}/milestones/${id}`, input).then((r) => r.data),
  remove: (tenantId: string, projectId: string, id: string) =>
    client.delete(`${base(tenantId)}/projects/${projectId}/milestones/${id}`),
};

export const dependencyApi = {
  get: (tenantId: string, taskId: string, signal?: AbortSignal) =>
    client.get<Dependencies>(`${base(tenantId)}/tasks/${taskId}/dependencies`, { signal }).then((r) => r.data),
  add: (tenantId: string, taskId: string, blockingTaskId: string) =>
    client.post<Dependencies>(`${base(tenantId)}/tasks/${taskId}/dependencies`, { blockingTaskId }).then((r) => r.data),
  remove: (tenantId: string, taskId: string, dependencyId: string) =>
    client.delete<Dependencies>(`${base(tenantId)}/tasks/${taskId}/dependencies/${dependencyId}`).then((r) => r.data),
  lookup: (tenantId: string, q: string, signal?: AbortSignal) =>
    client.get<TaskLookup[]>(`${base(tenantId)}/tasks/lookup`, { params: { q }, signal }).then((r) => r.data),
};

export const myWorkApi = {
  get: (tenantId: string, signal?: AbortSignal) =>
    client.get<MyWork>(`${base(tenantId)}/my-work`, { signal }).then((r) => r.data),
  focus: (tenantId: string, taskId: string) => client.put(`${base(tenantId)}/my-work/focus/${taskId}`),
  unfocus: (tenantId: string, taskId: string) => client.delete(`${base(tenantId)}/my-work/focus/${taskId}`),
};

export const planningApi = {
  calendar: (
    tenantId: string,
    params: { from: string; to: string; scope: CalendarScope; projectId?: string },
    signal?: AbortSignal,
  ) => client.get<CalendarData>(`${base(tenantId)}/calendar`, { params, signal }).then((r) => r.data),
  portfolio: (tenantId: string, signal?: AbortSignal) =>
    client.get<PortfolioRow[]>(`${base(tenantId)}/portfolio`, { signal }).then((r) => r.data),
  workload: (tenantId: string, weeks = 6, signal?: AbortSignal) =>
    client.get<Workload>(`${base(tenantId)}/workload`, { params: { weeks }, signal }).then((r) => r.data),
  setCapacity: (tenantId: string, memberId: string, weeklyCapacity: number | null) =>
    client.put<Workload>(`${base(tenantId)}/members/${memberId}/capacity`, { weeklyCapacity }).then((r) => r.data),
  member: (tenantId: string, memberId: string, signal?: AbortSignal) =>
    client.get<MemberOverview>(`${base(tenantId)}/members/${memberId}/overview`, { signal }).then((r) => r.data),
};

export type { Page };
