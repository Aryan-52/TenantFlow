import { useEffect, useState, useMemo } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTenant } from '../contexts/TenantContext';
import client from '../api/client';
import { apiError, projectApi, type Project } from '../api/work';
import type { Member } from './Members';
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
import { ProjectCard } from '../components/projects/ProjectCard';
import { ProjectsIllustration } from '../components/illustrations/Illustrations';
import { Avatar } from '../components/ui/Avatar';
import { Skeleton } from '../components/ui/Skeleton';
import { useToast } from '../contexts/ToastContext';
import { timeAgo } from '../api/activity';
import { initials, tintStyle } from '../components/ui/visual';
import { PROJECT_STATUS_LABELS } from '../components/tasks/taskMeta';
import type { ProjectStatus } from '../api/work';
import { Plus, Pencil, Trash2, LayoutGrid, List, SearchX } from 'lucide-react';

type SortOption = 'name-asc' | 'name-desc' | 'newest' | 'oldest';

const SORT_OPTIONS: { value: SortOption; label: string }[] = [
  { value: 'newest', label: 'Newest first' },
  { value: 'oldest', label: 'Oldest first' },
  { value: 'name-asc', label: 'Name (A–Z)' },
  { value: 'name-desc', label: 'Name (Z–A)' },
];

type ViewMode = 'grid' | 'list';
const VIEW_KEY = 'tf.projectsView';
const readView = (): ViewMode => {
  try {
    return localStorage.getItem(VIEW_KEY) === 'list' ? 'list' : 'grid';
  } catch {
    return 'grid';
  }
};
const STATUS_ORDER: ProjectStatus[] = ['ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED'];

const Projects = () => {
  const { currentTenant } = useTenant();
  const toast = useToast();
  const [view, setViewState] = useState<ViewMode>(readView);
  const setView = (v: ViewMode) => {
    setViewState(v);
    try {
      localStorage.setItem(VIEW_KEY, v);
    } catch {
      // Not persisted without storage.
    }
  };
  const [projects, setProjects] = useState<Project[]>([]);
  const [members, setMembers] = useState<Member[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [search, setSearch] = useState('');
  const [searchParams, setSearchParams] = useSearchParams();
  // Deep links: ?status=ACTIVE (e.g. from analytics) and ?new=1 (e.g. from the command palette).
  const [statusFilter, setStatusFilter] = useState(() => {
    const s = searchParams.get('status');
    return s && ['ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED'].includes(s) ? s : '';
  });
  const [sort, setSort] = useState<SortOption>('newest');

  const [showForm, setShowForm] = useState(() => searchParams.get('new') === '1');
  const [editing, setEditing] = useState<Project | null>(null);

  const [deleting, setDeleting] = useState<Project | null>(null);
  const [deleteError, setDeleteError] = useState('');
  const [deleteLoading, setDeleteLoading] = useState(false);

  const canManage = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';

  useEffect(() => {
    fetchProjects();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentTenant?.id]);

  // Eligible owners for the create/edit form. Only managers can open that form.
  const tenantId = currentTenant?.id;
  useEffect(() => {
    if (!tenantId || !canManage) return;
    let cancelled = false;
    client
      .get<Member[]>(`/api/tenants/${tenantId}/members`)
      .then((res) => {
        if (!cancelled) setMembers(res.data);
      })
      .catch(() => {
        // Non-fatal: the form still shows the current owner and the backend validates.
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId, canManage]);

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
      toast.success(`Project "${deleting.name}" deleted`);
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
  const statusCounts = STATUS_ORDER.map((st) => ({ status: st, count: projects.filter((p) => p.status === st).length }));
  const href = (p: Project) => `/tenants/${currentTenant.id}/projects/${p.id}`;

  return (
    <div>
      <PageHeader
        title="Projects"
        subtitle={`Every initiative in ${currentTenant.name}, with its progress, owner and latest activity.`}
        actions={
          canManage ? (
            <Button onClick={openCreate}>
              <Plus size={16} /> New project
            </Button>
          ) : undefined
        }
      />

      <ErrorState message={error} />

      <ProjectFormModal
        isOpen={showForm && canManage}
        onClose={() => {
          setShowForm(false);
          if (searchParams.has('new')) {
            searchParams.delete('new');
            setSearchParams(searchParams, { replace: true });
          }
        }}
        tenantId={currentTenant.id}
        project={editing}
        members={members}
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

      <div className="projects-toolbar">
        <SearchInput value={search} onChange={setSearch} placeholder="Search projects..." className="projects-search" />
        <div className="chip-group" role="group" aria-label="Filter projects by status">
          <button type="button" className="chip" aria-pressed={statusFilter === ''} onClick={() => setStatusFilter('')}>
            All <span className="chip-count">{projects.length}</span>
          </button>
          {statusCounts.map(({ status, count }) => (
            <button
              key={status}
              type="button"
              className="chip"
              aria-pressed={statusFilter === status}
              onClick={() => setStatusFilter(statusFilter === status ? '' : status)}
            >
              {PROJECT_STATUS_LABELS[status]} <span className="chip-count">{count}</span>
            </button>
          ))}
        </div>
        <div className="projects-toolbar-end">
          <Select value={sort} onChange={(e) => setSort(e.target.value as SortOption)} options={SORT_OPTIONS} aria-label="Sort projects" />
          <div className="segmented" role="group" aria-label="Layout">
            <button type="button" className={view === 'grid' ? 'active' : ''} aria-pressed={view === 'grid'} onClick={() => setView('grid')} aria-label="Grid view" data-tooltip="Grid">
              <LayoutGrid size={15} />
            </button>
            <button type="button" className={view === 'list' ? 'active' : ''} aria-pressed={view === 'list'} onClick={() => setView('list')} aria-label="List view" data-tooltip="List">
              <List size={15} />
            </button>
          </div>
        </div>
      </div>

      {loading ? (
        view === 'grid' ? (
          <div className="project-grid" aria-busy="true" aria-label="Loading projects">
            {Array.from({ length: 6 }, (_, i) => (
              <Skeleton key={i} style={{ height: '236px', borderRadius: 'var(--radius-xl)' }} />
            ))}
          </div>
        ) : (
          <TableSkeleton rows={4} cols={5} />
        )
      ) : filteredProjects.length === 0 ? (
        hasFilters ? (
          <EmptyState
            icon={<SearchX size={22} />}
            title="No matching projects"
            description="Try a different search or status filter."
            action={
              <Button
                variant="secondary"
                onClick={() => {
                  setSearch('');
                  setStatusFilter('');
                }}
              >
                Clear filters
              </Button>
            }
          />
        ) : (
          <EmptyState
            illustration={<ProjectsIllustration />}
            title="No projects yet"
            description={
              canManage
                ? 'Projects organize tasks, boards and calendars for each initiative. Create the first one for your team.'
                : 'Projects created by your workspace admins will appear here.'
            }
            action={
              canManage ? (
                <Button onClick={openCreate}>
                  <Plus size={16} /> Create project
                </Button>
              ) : undefined
            }
          />
        )
      ) : view === 'grid' ? (
        <div className="project-grid">
          {filteredProjects.map((p) => (
            <ProjectCard key={p.id} project={p} href={href(p)} canManage={canManage} onEdit={openEdit} onDelete={setDeleting} />
          ))}
        </div>
      ) : (
        <Table>
          <thead>
            <tr>
              <th>Project</th>
              <th>Status</th>
              <th>Progress</th>
              <th>Owner</th>
              <th>Updated</th>
              {canManage && (
                <th style={{ width: '96px' }}>
                  <span className="sr-only">Actions</span>
                </th>
              )}
            </tr>
          </thead>
          <tbody>
            {filteredProjects.map((p) => {
              const percent = p.taskCount ? Math.round((p.completedTaskCount / p.taskCount) * 100) : 0;
              return (
                <tr key={p.id}>
                  <td style={{ maxWidth: '340px' }}>
                    <div className="flex items-center gap-3">
                      <span className="project-mark" style={tintStyle(p.id)} aria-hidden="true">
                        {initials(p.name)}
                      </span>
                      <div style={{ minWidth: 0 }}>
                        <Link to={href(p)} className="table-primary-link">
                          {p.name}
                        </Link>
                        {p.description && <div className="text-xs text-muted mt-1 truncate">{p.description}</div>}
                      </div>
                    </div>
                  </td>
                  <td>
                    <ProjectStatusBadge status={p.status} />
                  </td>
                  <td style={{ minWidth: '160px' }}>
                    <div className="flex items-center gap-2">
                      <div className={`progress flex-1 ${percent === 100 ? 'is-complete' : ''}`.trim()} aria-label={`${percent}% complete`}>
                        <div className="progress-bar" style={{ width: `${percent}%` }} />
                      </div>
                      <span className="text-xs text-muted tabular">
                        {p.completedTaskCount}/{p.taskCount}
                      </span>
                    </div>
                  </td>
                  <td className="text-sm">
                    {p.ownerName ? (
                      <span className="assignee">
                        <Avatar name={p.ownerName} size="xs" />
                        {p.ownerName}
                      </span>
                    ) : (
                      <span className="text-faint">No owner</span>
                    )}
                  </td>
                  <td className="text-muted text-sm" title={new Date(p.updatedAt).toLocaleString()}>
                    {timeAgo(p.updatedAt)}
                  </td>
                  {canManage && (
                    <td>
                      <div className="flex items-center gap-1 justify-end">
                        <Button variant="ghost" size="sm" className="btn-icon" onClick={() => openEdit(p)} aria-label={`Edit ${p.name}`} data-tooltip="Edit">
                          <Pencil size={15} />
                        </Button>
                        <Button variant="ghost" size="sm" className="btn-icon text-danger" onClick={() => setDeleting(p)} aria-label={`Delete ${p.name}`} data-tooltip="Delete">
                          <Trash2 size={15} />
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
