import client from './client';
import type { Page } from './activity';

export interface TaskComment {
  id: string;
  taskId: string;
  authorId: string | null;
  /** Null when the author's account no longer exists. */
  authorName: string | null;
  body: string;
  createdAt: string;
  updatedAt: string;
  edited: boolean;
}

export const COMMENT_MAX = 4000;

const url = (tenantId: string, projectId: string, taskId: string) =>
  `/api/tenants/${tenantId}/projects/${projectId}/tasks/${taskId}/comments`;

/** A task's discussion (see TaskCommentController). Oldest first. */
export const commentApi = {
  list: (tenantId: string, projectId: string, taskId: string, signal?: AbortSignal) =>
    client.get<Page<TaskComment>>(url(tenantId, projectId, taskId), { params: { size: 100 }, signal }).then((r) => r.data),
  /** mentionedUserIds: members written as "@Name" in the body; each is notified once. */
  create: (tenantId: string, projectId: string, taskId: string, body: string, mentionedUserIds: string[] = []) =>
    client.post<TaskComment>(url(tenantId, projectId, taskId), { body, mentionedUserIds }).then((r) => r.data),
  update: (tenantId: string, projectId: string, taskId: string, commentId: string, body: string, mentionedUserIds: string[] = []) =>
    client.put<TaskComment>(`${url(tenantId, projectId, taskId)}/${commentId}`, { body, mentionedUserIds }).then((r) => r.data),
  remove: (tenantId: string, projectId: string, taskId: string, commentId: string) =>
    client.delete(`${url(tenantId, projectId, taskId)}/${commentId}`),
};
