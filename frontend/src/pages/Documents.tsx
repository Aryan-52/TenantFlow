import { useState } from 'react';
import { Link } from 'react-router-dom';
import { Download, Eye, FileArchive, FileImage, FileSpreadsheet, FileStack, FileText, Trash2 } from 'lucide-react';
import { documentApi, type DocumentItem, type DocumentSort, type DocumentType } from '../api/workspace';
import { attachmentApi, fileKindLabel, formatBytes, previewKind } from '../api/attachments';
import { timeAgo, type Page } from '../api/activity';
import { apiError } from '../api/work';
import { useTenant } from '../contexts/TenantContext';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';
import { useLoad } from '../hooks/useLoad';
import { useWorkspaceData } from '../hooks/useWorkspaceData';
import { PageHeader } from '../components/ui/PageHeader';
import { SearchInput } from '../components/ui/SearchInput';
import { Select } from '../components/ui/Select';
import { Button } from '../components/ui/Button';
import { ErrorState } from '../components/ui/ErrorState';
import { EmptyState } from '../components/ui/EmptyState';
import { TableSkeleton } from '../components/ui/Skeleton';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { FilePreview } from '../components/attachments/FilePreview';
import { taskHref } from '../components/tasks/taskLinks';

const PAGE_SIZE = 25;

const iconFor = (contentType: string) => {
  if (contentType.startsWith('image/')) return FileImage;
  if (contentType.includes('spreadsheet') || contentType === 'text/csv') return FileSpreadsheet;
  if (contentType.includes('zip')) return FileArchive;
  return FileText;
};

/** Every file in the workspace, across projects and tasks. */
const Documents = () => {
  const { currentTenant } = useTenant();
  const { user } = useAuth();
  const toast = useToast();
  const tenantId = currentTenant?.id ?? '';
  const { projects } = useWorkspaceData(tenantId);
  const [q, setQ] = useState('');
  const [projectId, setProjectId] = useState('');
  const [type, setType] = useState<DocumentType>('');
  const [sort, setSort] = useState<DocumentSort>('newest');
  const [page, setPage] = useState(0);
  const [previewing, setPreviewing] = useState<DocumentItem | null>(null);
  const [deleting, setDeleting] = useState<DocumentItem | null>(null);
  const [busy, setBusy] = useState(false);

  const query = q.trim();
  const { data, error, loading, reload } = useLoad<Page<DocumentItem>>(
    (signal) => documentApi.list(tenantId, { q: query, projectId, type, sort, page, size: PAGE_SIZE }, signal),
    [tenantId, query, projectId, type, sort, page],
    'Could not load documents.',
  );
  const isAdmin = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';
  const filtersOn = !!(query || projectId || type);

  // Any filter change starts again from the first page.
  const set = <T,>(setter: (v: T) => void) => (v: T) => {
    setter(v);
    setPage(0);
  };

  const download = async (d: DocumentItem) => {
    try {
      await attachmentApi.download(tenantId, d.projectId, d);
    } catch (err) {
      toast.error(apiError(err, 'Could not download the file').message);
    }
  };

  const remove = async () => {
    if (!deleting) return;
    setBusy(true);
    try {
      await attachmentApi.remove(tenantId, deleting.projectId, deleting.id);
      toast.success(`Deleted ${deleting.filename}`);
      setDeleting(null);
      reload();
    } catch (err) {
      toast.error(apiError(err, 'Could not delete the file').message);
    } finally {
      setBusy(false);
    }
  };

  if (!currentTenant) return null;

  return (
    <div>
      <PageHeader
        title="Documents"
        subtitle={data ? `${data.totalElements} file${data.totalElements === 1 ? '' : 's'}${filtersOn ? ' match these filters' : ' across all projects and tasks'}` : 'Files across all projects and tasks'}
      />

      <div className="toolbar" role="search" aria-label="Filter documents">
        <SearchInput value={q} onChange={set(setQ)} placeholder="Search by file name" className="toolbar-grow" />
        <Select aria-label="Project" value={projectId} onChange={(e) => set(setProjectId)(e.target.value)}
          options={[{ value: '', label: 'All projects' }, ...projects.map((p) => ({ value: p.id, label: p.name }))]} />
        <Select aria-label="File type" value={type} onChange={(e) => set(setType)(e.target.value as DocumentType)}
          options={[
            { value: '', label: 'All types' },
            { value: 'image', label: 'Images' },
            { value: 'pdf', label: 'PDF' },
            { value: 'text', label: 'Text and CSV' },
            { value: 'office', label: 'Office documents' },
            { value: 'archive', label: 'Archives' },
          ]} />
        <Select aria-label="Sort" value={sort} onChange={(e) => set(setSort)(e.target.value as DocumentSort)}
          options={[
            { value: 'newest', label: 'Newest first' },
            { value: 'oldest', label: 'Oldest first' },
            { value: 'largest', label: 'Largest first' },
            { value: 'smallest', label: 'Smallest first' },
            { value: 'name', label: 'Name A–Z' },
          ]} />
      </div>

      <ErrorState message={error} />
      {!data ? (
        !error && <TableSkeleton rows={5} cols={5} />
      ) : data.items.length === 0 ? (
        filtersOn ? (
          <EmptyState variant="plain" title="No file matches these filters" description="Try another name, project or type." />
        ) : (
          <EmptyState icon={<FileStack size={22} />} title="No files yet" description="Files added to projects or tasks appear here." />
        )
      ) : (
        <>
          <div className="table-container" aria-busy={loading}>
            <table className="table documents-table">
              <caption className="sr-only">Files</caption>
              <thead>
                <tr>
                  <th scope="col">Name</th>
                  <th scope="col">Location</th>
                  <th scope="col" className="num">Size</th>
                  <th scope="col">Uploaded</th>
                  <th scope="col"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((d) => {
                  const Icon = iconFor(d.contentType);
                  const canDelete = isAdmin || d.uploadedById === user?.id;
                  return (
                    <tr key={d.id}>
                      <th scope="row">
                        <span className="cell-link">
                          <span className="file-icon" aria-hidden="true"><Icon size={16} /></span>
                          <span className="min-w-0">
                            <span className="truncate block">{d.filename}</span>
                            <span className="text-xs text-muted">{fileKindLabel(d.contentType)}</span>
                          </span>
                        </span>
                      </th>
                      <td>
                        <Link to={`/tenants/${tenantId}/projects/${d.projectId}/files`}>{d.projectName ?? 'Project'}</Link>
                        {d.taskId && d.taskTitle && (
                          <div className="text-xs"><Link to={taskHref(tenantId, { id: d.taskId, projectId: d.projectId })} className="text-muted">Task: {d.taskTitle}</Link></div>
                        )}
                      </td>
                      <td className="num nowrap">{formatBytes(d.sizeBytes)}</td>
                      <td className="nowrap">
                        <span title={new Date(d.createdAt).toLocaleString()}>{timeAgo(d.createdAt)}</span>
                        <div className="text-xs text-muted">{d.uploadedByName ?? 'Unknown'}</div>
                      </td>
                      <td className="row-actions">
                        {previewKind(d.contentType) && (
                          <Button variant="ghost" size="sm" className="btn-icon" onClick={() => setPreviewing(d)} aria-label={`Preview ${d.filename}`} data-tooltip="Preview">
                            <Eye size={15} />
                          </Button>
                        )}
                        <Button variant="ghost" size="sm" className="btn-icon" onClick={() => download(d)} aria-label={`Download ${d.filename}`} data-tooltip="Download">
                          <Download size={15} />
                        </Button>
                        {canDelete && (
                          <Button variant="ghost" size="sm" className="btn-icon text-danger" onClick={() => setDeleting(d)} aria-label={`Delete ${d.filename}`} data-tooltip="Delete">
                            <Trash2 size={15} />
                          </Button>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {data.totalPages > 1 && (
            <nav className="pagination" aria-label="Pages">
              <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Previous</Button>
              <span className="text-sm text-muted">Page {page + 1} of {data.totalPages}</span>
              <Button variant="secondary" size="sm" disabled={!data.hasNext} onClick={() => setPage((p) => p + 1)}>Next</Button>
            </nav>
          )}
        </>
      )}

      <FilePreview
        tenantId={tenantId}
        projectId={previewing?.projectId ?? ''}
        attachment={previewing}
        onClose={() => setPreviewing(null)}
        onDownload={(a) => download(a as DocumentItem)}
        canRestore={!!previewing && (isAdmin || previewing.uploadedById === user?.id)}
        onRestored={reload}
      />
      <ConfirmDialog
        isOpen={!!deleting}
        title="Delete file"
        message={`Delete "${deleting?.filename ?? ''}"? It is removed from its ${deleting?.taskId ? 'task' : 'project'} for everyone. This cannot be undone.`}
        confirmText="Delete file"
        isDestructive
        isLoading={busy}
        onConfirm={remove}
        onCancel={() => setDeleting(null)}
      />
    </div>
  );
};

export default Documents;
