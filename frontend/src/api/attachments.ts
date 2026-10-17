import client from './client';
import type { Page } from './activity';

export interface Attachment {
  id: string;
  projectId: string;
  taskId: string | null;
  filename: string;
  contentType: string;
  sizeBytes: number;
  uploadedById: string | null;
  uploadedByName: string | null;
  createdAt: string;
}

/** Mirrors the server's allow-list (the server re-validates everything). */
export const ALLOWED_EXTENSIONS = ['pdf', 'png', 'jpg', 'jpeg', 'gif', 'webp', 'docx', 'xlsx', 'pptx', 'zip', 'txt', 'csv', 'md'];
export const MAX_FILE_BYTES = 10 * 1024 * 1024;

const base = (tenantId: string, projectId: string) => `/api/tenants/${tenantId}/projects/${projectId}/attachments`;

export const attachmentApi = {
  list: (tenantId: string, projectId: string, params: { taskId?: string; page?: number; size?: number } = {}) =>
    client.get<Page<Attachment>>(base(tenantId, projectId), { params }).then((r) => r.data),

  upload: (tenantId: string, projectId: string, file: File, taskId: string | undefined, onProgress?: (percent: number) => void) => {
    const form = new FormData();
    form.append('file', file);
    return client
      .post<Attachment>(base(tenantId, projectId), form, {
        params: taskId ? { taskId } : undefined,
        onUploadProgress: (e) => {
          if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100));
        },
      })
      .then((r) => r.data);
  },

  /** The file's bytes through the authorised API (never a public object URL). */
  fetchBlob: async (tenantId: string, projectId: string, attachmentId: string, signal?: AbortSignal) => {
    try {
      const res = await client.get<Blob>(`${base(tenantId, projectId)}/${attachmentId}/download`, { responseType: 'blob', signal });
      return res.data;
    } catch (err) {
      // Error bodies arrive as a Blob too: turn them back into JSON so callers see the message.
      const data = (err as { response?: { data?: unknown } })?.response?.data;
      if (data instanceof Blob) {
        try {
          (err as { response: { data: unknown } }).response.data = JSON.parse(await data.text());
        } catch {
          // Not JSON; keep the generic message.
        }
      }
      throw err;
    }
  },

  /** Downloads and saves the original file. */
  download: async (tenantId: string, projectId: string, attachment: Attachment) => {
    const blob = await attachmentApi.fetchBlob(tenantId, projectId, attachment.id);
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = attachment.filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  },

  /** Puts the original bytes back into a record whose stored contents are missing. */
  restoreContent: (tenantId: string, projectId: string, attachmentId: string, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return client.put<Attachment>(`${base(tenantId, projectId)}/${attachmentId}/content`, form).then((r) => r.data);
  },

  remove: (tenantId: string, projectId: string, attachmentId: string) =>
    client.delete(`${base(tenantId, projectId)}/${attachmentId}`),
};

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Client-side pre-check for a fast, friendly error; returns an error message or null. */
export function precheckFile(file: File): string | null {
  const ext = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : '';
  if (!ALLOWED_EXTENSIONS.includes(ext)) {
    return `"${file.name}" is not an allowed file type. Allowed: ${ALLOWED_EXTENSIONS.join(', ')}.`;
  }
  if (file.size === 0) return `"${file.name}" is empty.`;
  if (file.size > MAX_FILE_BYTES) return `"${file.name}" is larger than the 10 MB limit.`;
  return null;
}

export type PreviewKind = 'image' | 'pdf' | 'text';

/** Formats the app can show inline (rendered from the downloaded bytes, never from a URL). */
export function previewKind(contentType: string): PreviewKind | null {
  if (['image/png', 'image/jpeg', 'image/gif', 'image/webp'].includes(contentType)) return 'image';
  if (contentType === 'application/pdf') return 'pdf';
  if (['text/plain', 'text/csv', 'text/markdown'].includes(contentType)) return 'text';
  return null;
}

/** A short, human label for a stored content type. */
export function fileKindLabel(contentType: string): string {
  const map: Record<string, string> = {
    'application/pdf': 'PDF',
    'image/png': 'PNG image',
    'image/jpeg': 'JPEG image',
    'image/gif': 'GIF image',
    'image/webp': 'WebP image',
    'text/plain': 'Text',
    'text/csv': 'CSV',
    'text/markdown': 'Markdown',
    'application/zip': 'ZIP archive',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'Word document',
    'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet': 'Excel workbook',
    'application/vnd.openxmlformats-officedocument.presentationml.presentation': 'PowerPoint',
  };
  return map[contentType] ?? 'File';
}
