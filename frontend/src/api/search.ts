import client from './client';
import type { Page } from './activity';

export interface ProjectHit {
  id: string;
  tenantId: string;
  tenantName: string;
  name: string;
  status: string;
  description: string | null;
}

export interface TaskHit {
  id: string;
  tenantId: string;
  tenantName: string;
  projectId: string;
  projectName: string;
  title: string;
  status: string;
  priority: string;
  dueDate: string | null;
}

export interface MemberHit {
  userId: string;
  tenantId: string;
  tenantName: string;
  name: string;
  email: string;
  role: string;
}

export interface FileHit {
  id: string;
  tenantId: string;
  tenantName: string;
  projectId: string;
  projectName: string | null;
  taskId: string | null;
  filename: string;
  contentType: string;
  sizeBytes: number;
}

export interface SearchResult {
  query: string;
  projects: Page<ProjectHit> | null;
  tasks: Page<TaskHit> | null;
  members: Page<MemberHit> | null;
  files: Page<FileHit> | null;
}

export const SEARCH_MIN_LENGTH = 2;

export const searchApi = {
  search: (q: string, signal?: AbortSignal, size = 5) =>
    client.get<SearchResult>('/api/search', { params: { q, size }, signal }).then((r) => r.data),
};
