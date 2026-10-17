import client from './client';
import type { ProjectStatus, Task } from './work';

export interface ProjectProgress {
  id: string;
  name: string;
  status: ProjectStatus;
  ownerName: string | null;
  totalTasks: number;
  completedTasks: number;
  updatedAt: string;
}

/** See DashboardResponse on the backend: tenant-wide counts first, then the current user's data. */
export interface DashboardData {
  totalProjects: number;
  totalTasks: number;
  todoTasks: number;
  inProgressTasks: number;
  doneTasks: number;
  totalMembers: number;
  overdueTasks: number;
  dueSoonTasks: number;
  currentUserRole: string;
  myOpenTasks: number;
  myTasks: Task[];
  upcomingTasks: Task[];
  recentProjects: ProjectProgress[];
}

export const dashboardApi = {
  get: (tenantId: string, signal?: AbortSignal) =>
    client.get<DashboardData>(`/api/tenants/${tenantId}/dashboard`, { signal }).then((r) => r.data),
};
