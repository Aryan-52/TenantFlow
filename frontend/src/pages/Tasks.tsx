import { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import client from '../api/client';
import { apiError, projectApi, taskApi, type Project, type Task } from '../api/work';
import { useTenant } from '../contexts/TenantContext';
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
import { CheckSquare, Plus } from 'lucide-react';

const Tasks = () => {
  const { currentTenant } = useTenant();
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
      <PageHeader
        title="Tasks"
        subtitle="Manage work items across your projects."
        actions={
          <div className="flex items-center gap-2 flex-wrap">
            {projects.length > 0 && (
              <Select
                value={selectedProjectId}
                onChange={(e) => setSelectedProjectId(e.target.value)}
                options={projects.map((p) => ({ value: p.id, label: p.name }))}
                aria-label="Select project"
                style={{ minWidth: '180px' }}
              />
            )}
            {selectedProjectId && (
              <Button onClick={openCreate}>
                <Plus size={16} /> Create task
              </Button>
            )}
          </div>
        }
      />

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

      {!selectedProjectId ? (
        <EmptyState
          icon={<CheckSquare size={24} />}
          title="No project selected"
          description={projects.length > 0 ? 'Select a project from the dropdown above to view tasks.' : 'Create a project first to start managing tasks.'}
          action={projects.length === 0 ? <Link to={`/tenants/${currentTenant.id}/projects`} className="btn btn-primary">Go to projects</Link> : undefined}
        />
      ) : loading ? (
        <TableSkeleton rows={5} cols={5} />
      ) : (
        <TaskTable
          tasks={tasks}
          members={members}
          onOpen={openTask}
          onStatusChange={statusChange.changeStatus}
          emptyAction={<Button onClick={openCreate}>Create task</Button>}
        />
      )}
    </div>
  );
};

/** Remount on workspace change so no projects/tasks from the previous workspace remain. */
const TasksPage = () => {
  const { currentTenant } = useTenant();
  return <Tasks key={currentTenant?.id ?? 'none'} />;
};

export default TasksPage;
