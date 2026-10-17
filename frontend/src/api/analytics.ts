import client from './client';
import type { ProjectStatus, TaskPriority, TaskStatus } from './work';

export interface Signal {
  code:
    | 'OVERDUE'
    | 'HIGH_PRIORITY'
    | 'DUE_SOON'
    | 'STALE'
    | 'CONCENTRATION'
    | 'UNASSIGNED'
    | 'MILESTONE_OVERDUE'
    | 'BLOCKED'
    | 'TARGET_PASSED'
    | 'TARGET_NEAR'
    | 'BEHIND_SCHEDULE';
  severity: 'high' | 'medium' | 'low';
  message: string;
}

export interface ProjectHealth {
  projectId: string;
  name: string;
  status: ProjectStatus;
  ownerName: string | null;
  totalTasks: number;
  doneTasks: number;
  openTasks: number;
  overdue: number;
  dueSoon: number;
  highPriorityOpen: number;
  unassignedOpen: number;
  lastActivityAt: string;
  signals: Signal[];
  startDate: string | null;
  targetDate: string | null;
  blockedOpen: number;
  overdueMilestones: number;
}

export interface Workload {
  /** null = unassigned work. */
  userId: string | null;
  name: string | null;
  todo: number;
  inProgress: number;
  overdue: number;
}

export interface TrendPoint {
  date: string;
  created: number;
  completed: number;
  due: number;
  /** Open and past due at the end of the bucket (recorded dates only). */
  overdueOpen: number;
}

/** See AnalyticsResponse on the backend for exact definitions. */
export interface Analytics {
  generatedAt: string;
  range: { from: string; to: string; bucket: 'DAY' | 'WEEK' };
  projects: { total: number; byStatus: Record<ProjectStatus, number>; progress: { label: string; count: number }[] } | null;
  tasks: {
    total: number;
    byStatus: Record<TaskStatus, number>;
    open: number;
    overdue: number;
    dueSoon: number;
    unassignedOpen: number;
    openWithoutDueDate: number;
    completionRate: number | null;
    openByPriority: Record<TaskPriority, number>;
  };
  workload: Workload[];
  trend: TrendPoint[];
  delivery: {
    completed: number;
    onTime: number;
    late: number;
    withoutDueDate: number;
    onTimeRate: number | null;
    averageDaysToComplete: number | null;
    completionTrackedSince: string | null;
    untrackedDone: number;
  };
  health: ProjectHealth[];
  criteria: {
    dueSoonDays: number;
    dueSoonMin: number;
    staleDays: number;
    concentrationMinOpen: number;
    concentrationSharePercent: number;
    unassignedMin: number;
    targetNearDays: number;
    scheduleGapPercent: number;
  };
  flow: { blockedOpen: number; openAge: { label: string; count: number }[]; averageOpenAgeDays: number | null };
  milestones: {
    total: number;
    done: number;
    overdue: number;
    completedInRange: number;
    upcoming: { id: string; projectId: string; projectName: string; name: string; targetDate: string; status: string }[];
  };
}

export const analyticsApi = {
  get: (tenantId: string, params: { projectId?: string; from?: string; to?: string } = {}, signal?: AbortSignal) =>
    client.get<Analytics>(`/api/tenants/${tenantId}/analytics`, { params, signal }).then((r) => r.data),
};
