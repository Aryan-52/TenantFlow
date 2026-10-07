import { useEffect, useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import { useTenant } from '../contexts/TenantContext';
import { apiError, projectApi, type Project } from '../api/work';
import { Button } from '../components/ui/Button';
import { Select } from '../components/ui/Select';
import { SearchInput } from '../components/ui/SearchInput';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { ProjectStatusBadge } from '../components/ui/Badge';
import { Table } from '../components/ui/Table';
import { TableSkeleton } from '../components/ui/Skeleton';
import { EmptyState } from '../components/ui/EmptyState';
import { ErrorState } from '../components/ui/ErrorState';
import { PageHeader } from '../components/ui/PageHeader';
import { ProjectFormModal } from '../components/projects/ProjectFormModal';
import { PROJECT_STATUS_OPTIONS } from '../components/tasks/taskMeta';
import { FolderKanban, Plus, Edit2, Trash2 } from 'lucide-react';

type SortOption = 'name-asc' | 'name-desc' | 'newest' | 'oldest';

const SORT_OPTIONS: { value: SortOption; label: string }[] = [
  { value: 'newest', label: 'Newest first' },
  { value: 'oldest', label: 'Oldest first' },
  { value: 'name-asc', label: 'Name (A–Z)' },
  { value: 'name-desc', label: 'Name (Z–A)' },
];

const Projects = () => {
  const { currentTenant } = useTenant();
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [sort, setSort] = useState<SortOption>('newest');

  const [showForm, setShowForm] = useState(false);
  const [editing, setEditing] = useState<Project | null>(null);

  const [deleting, setDeleting] = useState<Project | null>(null);
  const [deleteError, setDeleteError] = useState('');
  const [deleteLoading, setDeleteLoading] = useState(false);

  const canManage = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';

  useEffect(() => {
    fetchProjects();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentTenant?.id]);

  const fetchProjects = async () => {
    if (!currentTenant) return;
    setLoading(true);
    setError('');
    try {
      setProjects(await projectApi.list(currentTenant.id));
    } catch (err) {
      setError(apiError(err, 'Failed to load projects').message);
    } finally {
      setLoading(false);
    }
  };

  const filteredProjects = useMemo(() => {
    const q = search.toLowerCase();
    const filtered = projects.filter(
      (p) =>
        (!statusFilter || p.status === statusFilter) &&
        (p.name.toLowerCase().includes(q) || (p.description && p.description.toLowerCase().includes(q)))
    );
    const sorted = [...filtered];
    switch (sort) {
      case 'name-asc':
        sorted.sort((a, b) => a.name.localeCompare(b.name));
        break;
      case 'name-desc':
        sorted.sort((a, b) => b.name.localeCompare(a.name));
        break;
      case 'oldest':
        sorted.sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
        break;
      case 'newest':
      default:
        sorted.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
    }
    return sorted;
  }, [projects, search, statusFilter, sort]);

  const handleDelete = async () => {
    if (!currentTenant || !deleting) return;
    setDeleteError('');
    setDeleteLoading(true);
    try {
      await projectApi.remove(currentTenant.id, deleting.id);
      setProjects((prev) => prev.filter((p) => p.id !== deleting.id));
      setDeleting(null);
    } catch (err) {
      setDeleteError(apiError(err, 'Failed to delete project').message);
      setDeleting(null);
    } finally {
      setDeleteLoading(false);
    }
  };

  const openCreate = () => {
    setEditing(null);
    setShowForm(true);
  };

  const openEdit = (project: Project) => {
    setEditing(project);
    setShowForm(true);
  };

  if (!currentTenant) return null;

  const hasFilters = !!(search || statusFilter);

  return (
    <div>
      <PageHeader
        title="Projects"
        subtitle="Manage workspaces for your team's initiatives."
        actions={
          canManage ? (
            <Button onClick={openCreate}>
              <Plus size={16} /> Create project
            </Button>
          ) : undefined
        }
      />

      <ErrorState message={error} />

      <ProjectFormModal
        isOpen={showForm}
        onClose={() => setShowForm(false)}
        tenantId={currentTenant.id}
        project={editing}
        onSaved={() => fetchProjects()}
      />

      <ConfirmDialog
        isOpen={!!deleting}
        title="Delete project"
        message={
          deleting
            ? `Delete "${deleting.name}"? This permanently deletes the project and its ${deleting.taskCount} task${deleting.taskCount === 1 ? '' : 's'}. This action cannot be undone.`
            : ''
        }
        confirmText="Delete project"
        isDestructive
        isLoading={deleteLoading}
        onConfirm={handleDelete}
        onCancel={() => {
          setDeleting(null);
          setDeleteError('');
        }}
      />
      {deleteError && <ErrorState message={deleteError} />}

      <div className="flex flex-col sm:flex-row gap-3 mb-4">
        <SearchInput value={search} onChange={setSearch} placeholder="Search projects..." className="max-w-md w-full" />
        <Select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          options={[{ value: '', label: 'All statuses' }, ...PROJECT_STATUS_OPTIONS]}
          style={{ maxWidth: '180px' }}
          aria-label="Filter projects by status"
        />
        <Select
          value={sort}
          onChange={(e) => setSort(e.target.value as SortOption)}
          options={SORT_OPTIONS}
          style={{ maxWidth: '200px' }}
          aria-label="Sort projects"
        />
      </div>

      {loading ? (
        <TableSkeleton rows={4} cols={5} />
      ) : filteredProjects.length === 0 ? (
        <EmptyState
          icon={<FolderKanban size={24} />}
          title="No projects found"
          description={hasFilters ? "We couldn't find any projects matching your filters." : 'Get started by creating your first project.'}
          action={!hasFilters && canManage ? <Button onClick={openCreate}>Create project</Button> : undefined}
        />
      ) : (
        <Table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Status</th>
              <th>Progress</th>
              <th>Owner</th>
              <th>Created</th>
              {canManage && <th style={{ width: '100px' }}>Actions</th>}
            </tr>
          </thead>
          <tbody>
            {filteredProjects.map((p) => {
              const percent = p.taskCount ? Math.round((p.completedTaskCount / p.taskCount) * 100) : 0;
              return (
                <tr key={p.id}>
                  <td style={{ maxWidth: '320px' }}>
                    <Link to={`/tenants/${currentTenant.id}/projects/${p.id}`} className="font-medium text-primary" style={{ textDecoration: 'none' }}>
                      {p.name}
                    </Link>
                    {p.description && <div className="text-xs text-muted mt-1 truncate">{p.description}</div>}
                  </td>
                  <td>
                    <ProjectStatusBadge status={p.status} />
                  </td>
                  <td style={{ minWidth: '140px' }}>
                    <div className="progress" aria-label={`${percent}% complete`}>
                      <div className="progress-bar" style={{ width: `${percent}%` }} />
                    </div>
                    <div className="text-xs text-muted mt-1">
                      {p.completedTaskCount}/{p.taskCount} tasks done
                    </div>
                  </td>
                  <td className="text-sm">{p.createdByName || <span className="text-muted">—</span>}</td>
                  <td className="text-muted text-sm">{new Date(p.createdAt).toLocaleDateString()}</td>
                  {canManage && (
                    <td>
                      <div className="flex items-center gap-1">
                        <Button variant="ghost" size="sm" className="btn-icon" onClick={() => openEdit(p)} title="Edit" aria-label={`Edit ${p.name}`}>
                          <Edit2 size={16} />
                        </Button>
                        <Button variant="ghost" size="sm" className="btn-icon text-danger" onClick={() => setDeleting(p)} title="Delete" aria-label={`Delete ${p.name}`}>
                          <Trash2 size={16} />
                        </Button>
                      </div>
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </Table>
      )}
    </div>
  );
};

export default Projects;
