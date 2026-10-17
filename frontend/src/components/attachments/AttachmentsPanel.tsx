import { useEffect, useRef, useState, type ChangeEvent, type DragEvent } from 'react';
import { Download, Eye, FileArchive, FileImage, FileSpreadsheet, FileText, RotateCcw, Trash2, UploadCloud, X } from 'lucide-react';
import { apiError } from '../../api/work';
import { timeAgo } from '../../api/activity';
import { ALLOWED_EXTENSIONS, attachmentApi, fileKindLabel, formatBytes, precheckFile, previewKind, type Attachment } from '../../api/attachments';
import { useAuth } from '../../contexts/AuthContext';
import { Button } from '../ui/Button';
import { ConfirmDialog } from '../ui/ConfirmDialog';
import { Skeleton } from '../ui/Skeleton';
import { FilePreview } from './FilePreview';

interface AttachmentsPanelProps {
  tenantId: string;
  projectId: string;
  /** Narrow to (and upload to) one task; omit for all files of the project. */
  taskId?: string;
  /** Workspace ADMIN/OWNER may delete anyone's files; others only their own. */
  canManage: boolean;
  /** Reports the number of stored files (e.g. for a tab label). */
  onCountChange?: (count: number) => void;
}

interface QueuedUpload {
  key: number;
  file: File;
  progress: number;
  error: string | null;
}

const fileIcon = (contentType: string) => {
  if (contentType.startsWith('image/')) return FileImage;
  if (contentType.includes('spreadsheet') || contentType === 'text/csv') return FileSpreadsheet;
  if (contentType === 'application/zip') return FileArchive;
  return FileText;
};

/** Upload (picker or drag and drop, several at once), list, preview, download and delete. */
export const AttachmentsPanel = ({ tenantId, projectId, taskId, canManage, onCountChange }: AttachmentsPanelProps) => {
  const { user } = useAuth();
  const [items, setItems] = useState<Attachment[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [queue, setQueue] = useState<QueuedUpload[]>([]);
  const [dragging, setDragging] = useState(false);
  const [toDelete, setToDelete] = useState<Attachment | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [previewing, setPreviewing] = useState<Attachment | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const nextKey = useRef(1);
  const countRef = useRef(onCountChange);
  useEffect(() => {
    countRef.current = onCountChange;
  });

  useEffect(() => {
    let cancelled = false;
    attachmentApi
      .list(tenantId, projectId, { taskId, size: 50 })
      .then((page) => {
        if (cancelled) return;
        setItems(page.items ?? []);
        setTotal(page.totalElements ?? page.items?.length ?? 0);
        setError('');
      })
      .catch((err) => {
        if (!cancelled) setError(apiError(err, 'Could not load files').message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId, projectId, taskId, reloadToken]);

  // Tell the parent (e.g. a tab badge) after the count changed - never during a render.
  useEffect(() => {
    if (!loading) countRef.current?.(total);
  }, [total, loading]);

  const changeTotal = (delta: number) => setTotal((t) => Math.max(0, t + delta));

  const uploadOne = async (entry: QueuedUpload) => {
    setQueue((q) => q.map((x) => (x.key === entry.key ? { ...x, progress: 0, error: null } : x)));
    try {
      const saved = await attachmentApi.upload(tenantId, projectId, entry.file, taskId, (progress) =>
        setQueue((q) => q.map((x) => (x.key === entry.key ? { ...x, progress } : x))),
      );
      setItems((prev) => [saved, ...prev]);
      changeTotal(1);
      setQueue((q) => q.filter((x) => x.key !== entry.key));
    } catch (err) {
      const message = apiError(err, 'Upload failed').message;
      setQueue((q) => q.map((x) => (x.key === entry.key ? { ...x, error: message } : x)));
    }
  };

  /** Validates every file first, then uploads the accepted ones one after another. */
  const addFiles = async (files: File[]) => {
    if (files.length === 0) return;
    const problems: string[] = [];
    const accepted: QueuedUpload[] = [];
    for (const file of files) {
      const problem = precheckFile(file);
      if (problem) problems.push(problem);
      else accepted.push({ key: nextKey.current++, file, progress: 0, error: null });
    }
    setError(problems.join(' '));
    if (accepted.length === 0) return;
    setQueue((q) => [...q, ...accepted]);
    for (const entry of accepted) {
      await uploadOne(entry);
    }
  };

  const onFileChosen = (e: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    e.target.value = '';
    void addFiles(files);
  };

  const onDrop = (e: DragEvent) => {
    e.preventDefault();
    setDragging(false);
    void addFiles(Array.from(e.dataTransfer.files ?? []));
  };

  const download = async (a: Attachment) => {
    setError('');
    try {
      await attachmentApi.download(tenantId, projectId, a);
    } catch (err) {
      setError(apiError(err, 'Download failed').message);
    }
  };

  const confirmDelete = async () => {
    if (!toDelete) return;
    setDeleting(true);
    try {
      await attachmentApi.remove(tenantId, projectId, toDelete.id);
      setItems((prev) => prev.filter((x) => x.id !== toDelete.id));
      changeTotal(-1);
      setToDelete(null);
    } catch (err) {
      setError(apiError(err, 'Could not delete the file').message);
      setToDelete(null);
    } finally {
      setDeleting(false);
    }
  };

  const uploading = queue.some((q) => q.error === null);

  return (
    <div className="attachments">
      <input
        ref={inputRef}
        type="file"
        multiple
        className="sr-only"
        accept={ALLOWED_EXTENSIONS.map((e) => `.${e}`).join(',')}
        onChange={onFileChosen}
        aria-label="Choose a file to upload"
        tabIndex={-1}
      />
      <div
        className={`dropzone ${dragging ? 'is-dragging' : ''}`.trim()}
        onDragOver={(e) => {
          if (!e.dataTransfer.types.includes('Files')) return;
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={(e) => {
          if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragging(false);
        }}
        onDrop={onDrop}
      >
        <UploadCloud size={20} aria-hidden="true" />
        <div className="dropzone-text">
          <span>
            Drop files here or{' '}
            <button type="button" className="link-button text-primary font-semibold" onClick={() => inputRef.current?.click()}>
              browse
            </button>
          </span>
          <span className="text-xs text-muted">PDF, images, Office, text and ZIP · up to 10 MB each</span>
        </div>
      </div>

      {queue.length > 0 && (
        <ul className="upload-queue" aria-label="Uploads" aria-live="polite">
          {queue.map((q) => (
            <li key={q.key} className={`upload-item ${q.error ? 'is-failed' : ''}`.trim()}>
              <div className="upload-item-main">
                <span className="truncate font-medium text-sm">{q.file.name}</span>
                {q.error ? (
                  <span className="text-xs text-danger" role="alert">{q.error}</span>
                ) : (
                  <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={q.progress} aria-label={`Uploading ${q.file.name}, ${q.progress}%`}>
                    <div className="progress-bar" style={{ width: `${q.progress}%` }} />
                  </div>
                )}
              </div>
              {q.error ? (
                <span className="flex gap-1">
                  <button type="button" className="icon-button" onClick={() => void uploadOne(q)} aria-label={`Retry ${q.file.name}`} data-tooltip="Retry">
                    <RotateCcw size={14} />
                  </button>
                  <button type="button" className="icon-button" onClick={() => setQueue((all) => all.filter((x) => x.key !== q.key))} aria-label={`Dismiss ${q.file.name}`} data-tooltip="Dismiss">
                    <X size={14} />
                  </button>
                </span>
              ) : (
                <span className="text-xs text-muted tabular">{q.progress}%</span>
              )}
            </li>
          ))}
        </ul>
      )}

      {error && (
        <p className="text-sm text-danger mt-2" role="alert">
          {error}{' '}
          {items.length === 0 && !loading && (
            <button type="button" className="link-button text-primary" onClick={() => { setLoading(true); setReloadToken((t) => t + 1); }}>
              Retry
            </button>
          )}
        </p>
      )}

      {loading ? (
        <div className="mt-3"><Skeleton style={{ height: '2.75rem' }} /></div>
      ) : items.length === 0 ? (
        !uploading && <p className="text-sm text-muted mt-3">No files yet.</p>
      ) : (
        <ul className="attachment-list mt-3" aria-label={`${total} file${total === 1 ? '' : 's'}`}>
          {items.map((a) => {
            const canDelete = canManage || (user != null && a.uploadedById === user.id);
            const Icon = fileIcon(a.contentType);
            const previewable = previewKind(a.contentType) != null;
            return (
              <li key={a.id} className="attachment-item">
                <span className="attachment-icon" aria-hidden="true"><Icon size={16} /></span>
                <div className="attachment-meta">
                  {previewable ? (
                    <button type="button" className="link-button attachment-name" title={a.filename} onClick={() => setPreviewing(a)}>
                      {a.filename}
                    </button>
                  ) : (
                    <span className="attachment-name" title={a.filename}>{a.filename}</span>
                  )}
                  <span className="text-xs text-muted">
                    {formatBytes(a.sizeBytes)} · {a.uploadedByName ?? 'Former member'} · {timeAgo(a.createdAt)}
                    <span className="sr-only"> · {fileKindLabel(a.contentType)}</span>
                  </span>
                </div>
                {previewable && (
                  <Button type="button" variant="ghost" size="sm" className="btn-icon" onClick={() => setPreviewing(a)} aria-label={`Preview ${a.filename}`} data-tooltip="Preview">
                    <Eye size={15} />
                  </Button>
                )}
                <Button type="button" variant="ghost" size="sm" className="btn-icon" onClick={() => download(a)} aria-label={`Download ${a.filename}`} data-tooltip="Download">
                  <Download size={15} />
                </Button>
                {canDelete && (
                  <Button type="button" variant="ghost" size="sm" className="btn-icon text-danger" onClick={() => setToDelete(a)} aria-label={`Delete ${a.filename}`} data-tooltip="Delete">
                    <Trash2 size={15} />
                  </Button>
                )}
              </li>
            );
          })}
        </ul>
      )}

      <FilePreview
        tenantId={tenantId}
        projectId={projectId}
        attachment={previewing}
        onClose={() => setPreviewing(null)}
        onDownload={download}
        canRestore={!!previewing && (canManage || (user != null && previewing.uploadedById === user.id))}
        onRestored={(updated) => setItems((prev) => prev.map((x) => (x.id === updated.id ? updated : x)))}
      />

      <ConfirmDialog
        isOpen={!!toDelete}
        title="Delete file"
        message={`Delete "${toDelete?.filename ?? ''}"? This cannot be undone.`}
        confirmText="Delete file"
        isDestructive
        isLoading={deleting}
        onConfirm={confirmDelete}
        onCancel={() => setToDelete(null)}
      />
    </div>
  );
};
