import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import client from '../api/client';
import { apiError, isTaskFocus, projectApi, taskApi, TASK_FOCUS_LABELS, type Project, type Task, type TaskFocus } from '../api/work';
import { useTenant } from '../contexts/TenantContext';
import { useAuth } from '../contexts/AuthContext';
import type { Member } from './Members';
import { Button } from '../components/ui/Button';
import { Select } from '../components/ui/Select';
import { TableSkeleton } from '../components/ui/Skeleton';
import { EmptyState } from '../components/ui/EmptyState';
import { ErrorState } from '../components/ui/ErrorState';
import { PageHeader } from '../components/ui/PageHeader';
import { TaskTable } from '../components/tasks/TaskTable';
import { TaskModal } from '../components/tasks/TaskModal';
import { useTaskStatusChange } from '../components/tasks/useTaskStatusChange';
import { useBulkTaskActions } from '../components/tasks/useBulkTaskActions';
import { QuickCreateTask } from '../components/tasks/QuickCreateTask';
import { CheckSquare, Plus } from 'lucide-react';

const Tasks = () => {
  const { currentTenant } = useTenant();
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();

  const [projects, setProjects] = useState<Project[]>([]);
  const [members, setMembers] = useState<Member[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>(searchParams.get('projectId') || '');

  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [modalOpen, setModalOpen] = useState(false);
  const [activeTask, setActiveTask] = useState<Task | null>(null);

  const canDelete = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';
  const tenantId = currentTenant?.id;
  const statusChange = useTaskStatusChange(tenantId, setTasks);
  const bulkActions = useBulkTaskActions(tenantId, setTasks);

  // Load the workspace's projects and members. The page is remounted per workspace (see
  // TasksPage), so state starts empty; a late response for another workspace is ignored.
  useEffect(() => {
    if (!tenantId) return;
    let cancelled = false;
    Promise.all([projectApi.list(tenantId), client.get<Member[]>(`/api/tenants/${tenantId}/members`)])
      .then(([projectList, memRes]) => {
        if (cancelled) return;
        setProjects(projectList);
        setMembers(memRes.data);
        // Keep the selection (e.g. from ?projectId=) only if it belongs to this workspace.
        setSelectedProjectId((prev) => (projectList.some((p) => p.id === prev) ? prev : projectList[0]?.id ?? ''));
        if (projectList.length === 0) setLoading(false);
      })
      .catch((err) => {
        if (cancelled) return;
        setError(apiError(err, 'Failed to load required data.').message);
        setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId]);

  // Load tasks only for a project known to belong to the current workspace.
  useEffect(() => {
    if (!tenantId || !projects.some((p) => p.id === selectedProjectId)) return;
    let cancelled = false;
    setSearchParams({ projectId: selectedProjectId }, { replace: true });
    setTasks([]);
    setError('');
    setLoading(true);
    taskApi
      .list(tenantId, selectedProjectId)
      .then((list) => {
        if (!cancelled) setTasks(list);
      })
      .catch((err) => {
        if (!cancelled) setError(apiError(err, 'Failed to load tasks').message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tenantId, selectedProjectId, projects]);

  const handleSaved = (saved: Task) => {
    setTasks((prev) => {
      // Moved to another project: it no longer belongs in this list.
      if (saved.projectId !== selectedProjectId) return prev.filter((t) => t.id !== saved.id);
      return prev.some((t) => t.id === saved.id) ? prev.map((t) => (t.id === saved.id ? saved : t)) : [...prev, saved];
    });
  };

  const openCreate = () => {
    setActiveTask(null);
    setModalOpen(true);
  };

  const openTask = (task: Task) => {
    setActiveTask(task);
    setModalOpen(true);
  };

  if (!currentTenant) return null;

  return (
    <div>
      <PageHeader title="Tasks" subtitle="Manage work items across your projects." />

      <ErrorState message={error} />
      <ErrorState message={statusChange.error} />

      {selectedProjectId && (
        <TaskModal
          isOpen={modalOpen}
          onClose={() => setModalOpen(false)}
          tenantId={currentTenant.id}
          projectId={selectedProjectId}
          task={activeTask}
          members={members}
          projects={projects}
          canDelete={canDelete}
          onSaved={handleSaved}
          onDeleted={(id) => setTasks((prev) => prev.filter((t) => t.id !== id))}
        />
      )}

      {projects.length === 0 ? (
        loading ? (
          <TableSkeleton rows={5} cols={5} />
        ) : error ? null : (
          <EmptyState
            icon={<CheckSquare size={24} />}
            title="No projects yet"
            description="Create a project first to start managing tasks."
            action={<Link to={`/tenants/${currentTenant.id}/projects`} className="btn btn-primary">Go to projects</Link>}
          />
        )
      ) : (
        <TaskTable
          tasks={tasks}
          members={members}
          loading={loading}
          onOpen={openTask}
          onStatusChange={statusChange.changeStatus}
          bulk={{ ...bulkActions, remove: canDelete ? bulkActions.remove : undefined }}
          currentUserId={user?.id}
          viewsKey={`tf.taskViews.${currentTenant.id}`}
          emptyAction={<Button onClick={openCreate}>Create task</Button>}
          projectSelector={
            <div className="flex gap-2">
              <ViewSelect value={BY_PROJECT} />
              <Select
                value={selectedProjectId}
                onChange={(e) => setSelectedProjectId(e.target.value)}
                options={projects.map((p) => ({ value: p.id, label: p.name }))}
                aria-label="Project"
              />
            </div>
          }
          primaryAction={
            <Button onClick={openCreate} disabled={!selectedProjectId}>
              <Plus size={16} /> Create task
            </Button>
          }
        />
      )}
    </div>
  );
};

const FOCUS_PAGE_SIZE = 50;
const BY_PROJECT = '';

/** Switches between the per-project list and the cross-project focus views. */
const ViewSelect = ({ value }: { value: TaskFocus | typeof BY_PROJECT }) => {
  const navigate = useNavigate();
  const { currentTenant } = useTenant();
  return (
    <Select
      value={value}
      onChange={(e) => {
        const next = e.target.value;
        navigate(`/tenants/${currentTenant?.id}/tasks${next ? `?focus=${next}` : ''}`);
      }}
      options={[
        { value: BY_PROJECT, label: 'By project' },
        ...(Object.keys(TASK_FOCUS_LABELS) as TaskFocus[]).map((f) => ({ value: f, label: `All projects: ${TASK_FOCUS_LABELS[f]}` })),
      ]}
      aria-label="Task view"
    />
  );
};

/**
 * Open tasks across every project of the workspace for one dashboard focus (overdue, due
 * soon, mine, all open), paged from the server, soonest due first.
 */
const FocusTasks = ({ focus }: { focus: TaskFocus }) => {
  const { currentTenant } = useTenant();
  const { user } = useAuth();
  const tenantId = currentTenant?.id;
  const [projects, setProjects] = useState<Project[]>([]);
  const [members, setMembers] = useState<Member[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState('');
  const [retryToken, setRetryToken] = useState(0);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const requestKey = `${page}:${retryToken}`;
  const loading = loadedKey !== requestKey;

  const [modalOpen, setModalOpen] = useState(false);
  const [activeTask, setActiveTask] = useState<Task | null>(null);
  const canDelete = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';
  const statusChange = useTaskStatusChange(tenantId, setTasks);
  const bulkActions = useBulkTaskActions(tenantId, setTasks);
  const [creating, setCreating] = useState(false);

  // Projects and members feed the task dialog (move between projects, assignee picker).
  useEffect(() => {
    if (!tenantId) return;
    let cancelled = false;
    Promise.all([projectApi.list(tenantId), client.get<Member[]>(`/api/tenants/${tenantId}/members`)])
      .then(([projectList, memRes]) => {
        if (cancelled) return;
        setProjects(projectList);
        setMembers(memRes.data);
      })
      .catch(() => undefined); // the task list below reports load failures
    return () => {
      cancelled = true;
    };
  }, [tenantId]);

  useEffect(() => {
    if (!tenantId) return;
    const controller = new AbortController();
    taskApi
      .listForTenant(tenantId, focus, page, FOCUS_PAGE_SIZE, controller.signal)
      .then((res) => {
        setTasks((prev) => (page === 0 ? res.items : [...prev, ...res.items.filter((t) => !prev.some((p) => p.id === t.id))]));
        setHasNext(res.hasNext);
        setTotal(res.totalElements);
        setError('');
      })
      .catch((err) => {
        if (!controller.signal.aborted) setError(apiError(err, 'Failed to load tasks').message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadedKey(requestKey);
      });
    return () => controller.abort();
  }, [tenantId, focus, page, requestKey]);

  const handleSaved = (saved: Task) =>
    setTasks((prev) =>
      saved.status === 'DONE' ? prev.filter((t) => t.id !== saved.id) : prev.map((t) => (t.id === saved.id ? saved : t)),
    );

  if (!currentTenant) return null;

  return (
    <div>
      <PageHeader
        title={TASK_FOCUS_LABELS[focus]}
        subtitle={loading && tasks.length === 0 ? 'Across all projects in this workspace.' : `${total} across all projects in this workspace.`}
      />

      {error && (
        <div className="flex items-center gap-3 mb-4">
          <div className="flex-1"><ErrorState message={error} /></div>
          <Button variant="secondary" onClick={() => setRetryToken((n) => n + 1)}>Retry</Button>
        </div>
      )}
      <ErrorState message={statusChange.error} />

      {activeTask && (
        <TaskModal
          isOpen={modalOpen}
          onClose={() => setModalOpen(false)}
          tenantId={currentTenant.id}
          projectId={activeTask.projectId}
          task={activeTask}
          members={members}
          projects={projects}
          canDelete={canDelete}
          onSaved={handleSaved}
          onDeleted={(id) => setTasks((prev) => prev.filter((t) => t.id !== id))}
        />
      )}

      <TaskTable
        tasks={tasks}
        members={members}
        loading={loading && tasks.length === 0}
        onOpen={(task) => {
          setActiveTask(task);
          setModalOpen(true);
        }}
        onStatusChange={statusChange.changeStatus}
        bulk={{ ...bulkActions, remove: canDelete ? bulkActions.remove : undefined }}
          currentUserId={user?.id}
          viewsKey={`tf.taskViews.${currentTenant.id}`}
        projectSelector={<ViewSelect value={focus} />}
        primaryAction={
          <Button onClick={() => setCreating(true)}>
            <Plus size={16} /> Create task
          </Button>
        }
        showProject
      />

      <QuickCreateTask
        tenantId={currentTenant.id}
        isOpen={creating}
        onClose={() => setCreating(false)}
        canDelete={canDelete}
        onCreated={() => {
          setPage(0);
          setRetryToken((n) => n + 1);
        }}
      />

      {hasNext && (
        <div className="flex justify-center mt-4">
          <Button variant="secondary" onClick={() => setPage((p) => p + 1)} isLoading={loading} disabled={loading}>
            Load more ({tasks.length} of {total})
          </Button>
        </div>
      )}
    </div>
  );
};

/**
 * Remount on workspace or view change so nothing from the previous workspace remains and
 * a late response for it is never shown.
 */
const TasksPage = () => {
  const { currentTenant } = useTenant();
  const [searchParams] = useSearchParams();
  const focus = searchParams.get('focus');
  if (isTaskFocus(focus)) return <FocusTasks key={`${currentTenant?.id ?? 'none'}:${focus}`} focus={focus} />;
  return <Tasks key={currentTenant?.id ?? 'none'} />;
};

export default TasksPage;
