import client from './client';
import type { Page } from './activity';

// Documents, reports, automations and personal preferences.

export interface DocumentItem {
  id: string;
  projectId: string;
  projectName: string | null;
  taskId: string | null;
  taskTitle: string | null;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploadedById: string | null;
  uploadedByName: string | null;
  createdAt: string;
}

export type DocumentType = '' | 'image' | 'pdf' | 'text' | 'office' | 'archive';
export type DocumentSort = 'newest' | 'oldest' | 'largest' | 'smallest' | 'name';

export interface DocumentQuery {
  q?: string;
  projectId?: string;
  type?: DocumentType;
  sort?: DocumentSort;
  page?: number;
  size?: number;
}

const defined = (o: object) => Object.fromEntries(Object.entries(o).filter(([, v]) => v !== '' && v !== undefined && v !== null));

export const documentApi = {
  list: (tenantId: string, query: DocumentQuery, signal?: AbortSignal) =>
    client
      .get<Page<DocumentItem>>(`/api/tenants/${tenantId}/documents`, { params: defined(query), signal })
      .then((r) => r.data),
};

export type ReportType = 'project-status' | 'overdue-tasks' | 'workload' | 'task-completion' | 'milestones';

export interface ReportColumn {
  key: string;
  label: string;
  numeric: boolean;
}

export interface Report {
  type: ReportType;
  title: string;
  description: string;
  columns: ReportColumn[];
  rows: string[][];
  filters: Record<string, string>;
  generatedAt: string;
}

export interface ReportFilters {
  projectId?: string;
  assigneeId?: string;
  from?: string;
  to?: string;
}

export const reportApi = {
  get: (tenantId: string, type: ReportType, filters: ReportFilters, signal?: AbortSignal) =>
    client.get<Report>(`/api/tenants/${tenantId}/reports/${type}`, { params: defined(filters), signal }).then((r) => r.data),
  /** The server builds the CSV from the same filters; the browser only saves the file. */
  csv: (tenantId: string, type: ReportType, filters: ReportFilters) =>
    client
      .get<Blob>(`/api/tenants/${tenantId}/reports/${type}/csv`, { params: defined(filters), responseType: 'blob' })
      .then((r) => {
        const disposition = String(r.headers['content-disposition'] ?? '');
        const match = disposition.match(/filename="?([^";]+)"?/);
        return { blob: r.data, filename: match?.[1] ?? `tenantflow-${type}.csv` };
      }),
};

export type AutomationType =
  | 'OVERDUE_NOTIFY_OWNER'
  | 'URGENT_NOTIFY_OWNER'
  | 'UNBLOCKED_NOTIFY_ASSIGNEE'
  | 'MILESTONE_DONE_NOTIFY_ASSIGNEES';

export interface AutomationRule {
  type: AutomationType;
  trigger: string;
  action: string;
  enabled: boolean;
  updatedAt: string | null;
  runs: number;
  failures: number;
  lastRunAt: string | null;
}

export interface AutomationRun {
  id: string;
  type: AutomationType;
  taskId: string | null;
  outcome: 'SUCCESS' | 'SKIPPED' | 'FAILED';
  message: string;
  createdAt: string;
}

export const automationApi = {
  rules: (tenantId: string, signal?: AbortSignal) =>
    client.get<AutomationRule[]>(`/api/tenants/${tenantId}/automations`, { signal }).then((r) => r.data),
  toggle: (tenantId: string, type: AutomationType, enabled: boolean) =>
    client.put<AutomationRule[]>(`/api/tenants/${tenantId}/automations/${type}`, { enabled }).then((r) => r.data),
  runs: (tenantId: string, page = 0, size = 20, signal?: AbortSignal) =>
    client
      .get<Page<AutomationRun>>(`/api/tenants/${tenantId}/automations/runs`, { params: { page, size }, signal })
      .then((r) => r.data),
};

export interface Preferences {
  dueSoonEnabled: boolean;
  dueSoonDays: number;
  overdueEnabled: boolean;
  mentionsEnabled: boolean;
  timeZone: string | null;
  effectiveTimeZone: string;
}

export const preferenceApi = {
  get: () => client.get<Preferences>('/api/users/me/preferences').then((r) => r.data),
  update: (p: Omit<Preferences, 'effectiveTimeZone'>) =>
    client.put<Preferences>('/api/users/me/preferences', p).then((r) => r.data),
};

/** Saves a Blob as a file through a temporary object URL. */
export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
