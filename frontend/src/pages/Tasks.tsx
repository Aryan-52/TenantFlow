import { useEffect, useState } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import client from '../api/client';
import { apiError, projectApi, taskApi, type Project, type Task, type TaskStatus } from '../api/work';
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
  const [actionError, setActionError] = useState('');

  const [modalOpen, setModalOpen] = useState(false);
  const [activeTask, setActiveTask] = useState<Task | null>(null);

  const canDelete = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';

  useEffect(() => {
    if (currentTenant) {
      fetchProjectsAndMembers();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentTenant?.id]);

  useEffect(() => {
    if (selectedProjectId) {
      setSearchParams({ projectId: selectedProjectId }, { replace: true });
      fetchTasks();
    } else {
      setTasks([]);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProjectId, currentTenant?.id]);

  const fetchProjectsAndMembers = async () => {
    if (!currentTenant) return;
    try {
      const [projectList, memRes] = await Promise.all([
        projectApi.list(currentTenant.id),
        client.get(`/api/tenants/${currentTenant.id}/members`),
      ]);
      setProjects(projectList);
      setMembers(memRes.data);
      if (!selectedProjectId && projectList.length > 0) {
        setSelectedProjectId(projectList[0].id);
      } else if (projectList.length === 0) {
        setLoading(false);
      }
    } catch (err) {
      setError(apiError(err, 'Failed to load required data.').message);
      setLoading(false);
    }
  };

  const fetchTasks = async () => {
    if (!currentTenant || !selectedProjectId) return;
    setLoading(true);
    setError('');
    try {
      setTasks(await taskApi.list(currentTenant.id, selectedProjectId));
    } catch (err) {
      setError(apiError(err, 'Failed to load tasks').message);
    } finally {
      setLoading(false);
    }
  };

  const handleStatusChange = async (task: Task, status: TaskStatus) => {
    if (!currentTenant) return;
    setActionError('');
    try {
      const saved = await taskApi.updateStatus(currentTenant.id, task.projectId, task.id, status);
      setTasks((prev) => prev.map((t) => (t.id === saved.id ? saved : t)));
    } catch (err) {
      setActionError(apiError(err, 'Failed to update task status').message);
    }
  };

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
      <ErrorState message={actionError} />

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
          onStatusChange={handleStatusChange}
          emptyAction={<Button onClick={openCreate}>Create task</Button>}
        />
      )}
    </div>
  );
};

export default Tasks;
