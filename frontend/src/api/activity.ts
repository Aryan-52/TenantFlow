import client from './client';

/** Shape of every paginated collection returned by the backend (PageResponse). */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface AppNotification {
  id: string;
  type: string;
  title: string;
  body: string | null;
  /** In-app route to open, e.g. /tenants/:id/projects/:id/tasks?task=:id */
  link: string | null;
  tenantId: string | null;
  read: boolean;
  createdAt: string;
}

/** Inbox filters (groups of notification types, see NotificationService.CATEGORIES). */
export type NotificationCategory = 'all' | 'assigned' | 'mentions' | 'reminders' | 'updates' | 'automations';

export interface ActivityItem {
  id: string;
  type: string;
  summary: string;
  actorId: string | null;
  actorName: string | null;
  projectId: string | null;
  taskId: string | null;
  createdAt: string;
}

export const notificationApi = {
  list: (params: { unreadOnly?: boolean; category?: NotificationCategory; page?: number; size?: number } = {}) =>
    client.get<Page<AppNotification>>('/api/notifications', { params }).then((r) => r.data),
  unreadCount: () => client.get<{ count: number }>('/api/notifications/unread-count').then((r) => r.data.count),
  markRead: (id: string) => client.post<AppNotification>(`/api/notifications/${id}/read`).then((r) => r.data),
  markAllRead: () => client.post<{ updated: number }>('/api/notifications/read-all').then((r) => r.data.updated),
};

export const activityApi = {
  list: (tenantId: string, params: { projectId?: string; taskId?: string; page?: number; size?: number } = {}) =>
    client.get<Page<ActivityItem>>(`/api/tenants/${tenantId}/activity`, { params }).then((r) => r.data),
};

/** "just now", "5 minutes ago", "yesterday", ... */
export function timeAgo(iso: string, now: number = Date.now()): string {
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000);
  const rtf = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });
  const abs = Math.abs(seconds);
  if (abs < 45) return 'just now';
  if (abs < 3600) return rtf.format(Math.round(seconds / 60), 'minute');
  if (abs < 86400) return rtf.format(Math.round(seconds / 3600), 'hour');
  if (abs < 604800) return rtf.format(Math.round(seconds / 86400), 'day');
  return new Date(iso).toLocaleDateString();
}
