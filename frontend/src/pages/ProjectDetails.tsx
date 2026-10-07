import { useEffect, useMemo, useState } from 'react';
import { useParams, Link, NavLink, Navigate, useNavigate } from 'react-router-dom';
import client from '../api/client';
import { apiError, projectApi, taskApi, type Project, type Task } from '../api/work';
import { useTenant } from '../contexts/TenantContext';
import type { Member } from './Members';
import { Button } from '../components/ui/Button';
import { ErrorState } from '../components/ui/ErrorState';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { PriorityBadge, ProjectStatusBadge } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { EmptyState } from '../components/ui/EmptyState';
import { ProjectFormModal } from '../components/projects/ProjectFormModal';
import { TaskModal, type TaskDefaults } from '../components/tasks/TaskModal';
import { TaskTable } from '../components/tasks/TaskTable';
import { KanbanBoard } from '../components/tasks/KanbanBoard';
import { TaskCalendar } from '../components/tasks/TaskCalendar';
import { DueDateLabel } from '../components/tasks/DueDateLabel';
import { compareByDueThenPriority, dueState, isDueWithinDays } from '../components/tasks/taskMeta';
import { useTaskStatusChange } from '../components/tasks/useTaskStatusChange';
import { ArrowLeft, Trash2, Edit2, Plus, CheckSquare, LayoutDashboard, ListTodo, Columns3, CalendarDays } from 'lucide-react';

const VIEWS = [
  { key: '', label: 'Overview', icon: LayoutDashboard },
  { key: 'tasks', label: 'Tasks', icon: ListTodo },
  { key: 'kanban', label: 'Kanban', icon: Columns3 },
  { key: 'calendar', label: 'Calendar', icon: CalendarDays },
] as const;

const ProjectDetails = () => {
  const { projectId, view = '' } = useParams();
  const { currentTenant } = useTenant();
  const navigate = useNavigate();

  const [project, setProject] = useState<Project | null>(null);
  const [projects, setProjects] = useState<Project[]>([]);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [members, setMembers] = useState<Member[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');

  const [editProjectOpen, setEditProjectOpen] = useState(false);
  const [deleteProjectOpen, setDeleteProjectOpen] = useState(false);
  const [deleteProjectLoading, setDeleteProjectLoading] = useState(false);

  const [taskModalOpen, setTaskModalOpen] = useState(false);
  const [activeTask, setActiveTask] = useState<Task | null>(null);
  const [taskDefaults, setTaskDefaults] = useState<TaskDefaults | undefined>();

  const canManage = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';
  const basePath = `/tenants/${currentTenant?.id}/projects/${projectId}`;

  const tenantId = currentTenant?.id;
  const statusChange = useTaskStatusChange(tenantId, setTasks);

  // The component is remounted per workspace + project (see ProjectDetailsPage), so state
  // always starts empty; responses that arrive after a switch are ignored.
  useEffect(() => {
    if (!tenantId || !projectId) return;
    let cancelled = false;
    Promise.all([
      projectApi.get(tenantId, projectId),
      taskApi.list(tenantId, projectId),
      client.get<Member[]>(`/api/tenants/${tenantId}/members`),
      projectApi.list(tenantId),
    ])
      .then(([proj, taskList, membersRes, projectList]) => {
        if (cancelled) return;
        setProject(proj);
        setTasks(taskList);
        setMembers(membersRes.data);
        setProjects(projectList);
      })
      .catch((err) => {
        if (!cancelled) setError(apiError(err, 'Failed to load project details').message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId, projectId]);

  const handleDeleteProject = async () => {
    if (!currentTenant || !project) return;
    setDeleteProjectLoading(true);
    try {
      await projectApi.remove(currentTenant.id, project.id);
      navigate(`/tenants/${currentTenant.id}/projects`);
    } catch (err) {
      setActionError(apiError(err, 'Failed to delete project').message);
      setDeleteProjectLoading(false);
      setDeleteProjectOpen(false);
    }
  };

  const openCreateTask = (defaults?: TaskDefaults) => {
    setActiveTask(null);
    setTaskDefaults(defaults);
    setTaskModalOpen(true);
  };

  const openTask = (task: Task) => {
    setActiveTask(task);
    setTaskDefaults(undefined);
    setTaskModalOpen(true);
  };

  const handleTaskSaved = (saved: Task) => {
    setTasks((prev) => {
      if (saved.projectId !== projectId) return prev.filter((t) => t.id !== saved.id);
      return prev.some((t) => t.id === saved.id) ? prev.map((t) => (t.id === saved.id ? saved : t)) : [...prev, saved];
    });
  };

  const stats = useMemo(() => {
    const done = tasks.filter((t) => t.status === 'DONE').length;
    return {
      total: tasks.length,
      todo: tasks.filter((t) => t.status === 'TODO').length,
      inProgress: tasks.filter((t) => t.status === 'IN_PROGRESS').length,
      done,
      overdue: tasks.filter((t) => dueState(t) === 'overdue').length,
      percent: tasks.length ? Math.round((done / tasks.length) * 100) : 0,
    };
  }, [tasks]);

  const upcoming = useMemo(() => tasks.filter((t) => isDueWithinDays(t, 14)).sort(compareByDueThenPriority).slice(0, 6), [tasks]);

  if (!VIEWS.some((v) => v.key === view)) {
    return <Navigate to={basePath} replace />;
  }

  if (loading) {
    return (
      <div className="flex flex-col gap-6">
        <Skeleton style={{ height: '3rem', width: '30%' }} />
        <Skeleton style={{ height: '2.5rem' }} />
        <Skeleton style={{ height: '12rem' }} />
      </div>
    );
  }

  if (error) return <ErrorState message={error} />;
  if (!project || !currentTenant) return <ErrorState message="Project not found" />;

  const addTaskButton = (
    <Button size="sm" onClick={() => openCreateTask()}>
      <Plus size={14} /> Add task
    </Button>
  );

  return (
    <div>
      <div className="mb-4">
        <Link to={`/tenants/${currentTenant.id}/projects`} className="text-muted flex items-center gap-1 text-sm" style={{ textDecoration: 'none', width: 'fit-content' }}>
          <ArrowLeft size={16} /> Back to projects
        </Link>
      </div>

      <div className="flex justify-between items-start gap-4 mb-4 flex-col sm:flex-row">
        <div style={{ minWidth: 0 }}>
          <div className="flex items-center gap-3 flex-wrap mb-1">
            <h1 className="mb-0">{project.name}</h1>
            <ProjectStatusBadge status={project.status} />
          </div>
        </div>
        <div className="flex gap-2 flex-shrink-0">
          {addTaskButton}
          {canManage && (
            <>
              <Button variant="secondary" size="sm" onClick={() => setEditProjectOpen(true)}>
                <Edit2 size={14} /> Edit
              </Button>
              <Button variant="danger-outline" size="sm" onClick={() => setDeleteProjectOpen(true)}>
                <Trash2 size={14} /> Delete
              </Button>
            </>
          )}
        </div>
      </div>

      <nav className="tabs mb-6" aria-label="Project views">
        {VIEWS.map(({ key, label, icon: Icon }) => (
          <NavLink key={key || 'overview'} to={key ? `${basePath}/${key}` : basePath} end className={({ isActive }) => `tab-link ${isActive ? 'active' : ''}`.trim()}>
            <Icon size={15} aria-hidden="true" /> {label}
          </NavLink>
        ))}
      </nav>

      <ErrorState message={actionError || statusChange.error} />

      <ProjectFormModal
        isOpen={editProjectOpen}
        onClose={() => setEditProjectOpen(false)}
        tenantId={currentTenant.id}
        project={project}
        onSaved={setProject}
      />

      <ConfirmDialog
        isOpen={deleteProjectOpen}
        title="Delete project"
        message={`Delete "${project.name}"? This permanently deletes the project and its ${tasks.length} task${tasks.length === 1 ? '' : 's'}. This action cannot be undone.`}
        confirmText="Delete project"
        isDestructive
        isLoading={deleteProjectLoading}
        onConfirm={handleDeleteProject}
        onCancel={() => setDeleteProjectOpen(false)}
      />

      <TaskModal
        isOpen={taskModalOpen}
        onClose={() => setTaskModalOpen(false)}
        tenantId={currentTenant.id}
        projectId={project.id}
        task={activeTask}
        defaults={taskDefaults}
        members={members}
        projects={projects}
        canDelete={canManage}
        onSaved={handleTaskSaved}
        onDeleted={(id) => setTasks((prev) => prev.filter((t) => t.id !== id))}
      />

      {view === '' && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="card md:col-span-2">
            <div className="card-header">
              <h3 className="mb-0">About this project</h3>
            </div>
            <div className="card-body">
              <p className="text-secondary mb-4" style={{ whiteSpace: 'pre-wrap' }}>
                {project.description || <span className="text-muted">No description provided.</span>}
              </p>
              <dl className="detail-list">
                <div>
                  <dt>Status</dt>
                  <dd>
                    <ProjectStatusBadge status={project.status} />
                  </dd>
                </div>
                <div>
                  <dt>Owner</dt>
                  <dd>{project.createdByName || <span className="text-muted">Unknown</span>}</dd>
                </div>
                <div>
                  <dt>Created</dt>
                  <dd>{new Date(project.createdAt).toLocaleDateString()}</dd>
                </div>
                <div>
                  <dt>Last updated</dt>
                  <dd>{new Date(project.updatedAt).toLocaleDateString()}</dd>
                </div>
              </dl>
            </div>
          </div>

          <div className="card">
            <div className="card-header">
              <h3 className="mb-0">Progress</h3>
              <span className="text-sm font-semibold">{stats.percent}%</span>
            </div>
            <div className="card-body">
              <div className="progress mb-4" aria-label={`${stats.percent}% of tasks done`}>
                <div className="progress-bar" style={{ width: `${stats.percent}%` }} />
              </div>
              <ul className="stat-list">
                <li>
                  <span>To do</span>
                  <strong>{stats.todo}</strong>
                </li>
                <li>
                  <span>In progress</span>
                  <strong className="text-primary">{stats.inProgress}</strong>
                </li>
                <li>
                  <span>Done</span>
                  <strong className="text-success">{stats.done}</strong>
                </li>
                <li>
                  <span>Overdue</span>
                  <strong className={stats.overdue ? 'text-danger' : undefined}>{stats.overdue}</strong>
                </li>
              </ul>
            </div>
          </div>

          <div className="card md:col-span-3">
            <div className="card-header">
              <h3 className="mb-0">Due soon & overdue</h3>
              <Link to={`${basePath}/calendar`} className="text-sm text-primary" style={{ textDecoration: 'none' }}>
                Open calendar
              </Link>
            </div>
            <div className="card-body">
              {tasks.length === 0 ? (
                <EmptyState icon={<CheckSquare size={22} />} title="No tasks yet" description="Add the first task to get this project moving." action={addTaskButton} />
              ) : upcoming.length === 0 ? (
                <p className="text-sm text-muted">Nothing due in the next two weeks.</p>
              ) : (
                <ul className="flex flex-col gap-2">
                  {upcoming.map((t) => (
                    <li key={t.id}>
                      <button type="button" className="cal-day-item" onClick={() => openTask(t)}>
                        <span className="font-medium truncate">{t.title}</span>
                        <span className="flex items-center gap-2 flex-shrink-0">
                          <PriorityBadge priority={t.priority} />
                          <DueDateLabel task={t} />
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        </div>
      )}

      {view === 'tasks' && (
        <TaskTable tasks={tasks} members={members} onOpen={openTask} onStatusChange={statusChange.changeStatus} emptyAction={addTaskButton} />
      )}

      {view === 'kanban' && (
        <KanbanBoard
          tasks={tasks}
          pendingIds={statusChange.pendingIds}
          onOpen={openTask}
          onMove={statusChange.changeStatus}
          onCreate={(status) => openCreateTask({ status })}
        />
      )}

      {view === 'calendar' && <TaskCalendar tasks={tasks} onOpen={openTask} onCreate={(dueDate) => openCreateTask({ dueDate })} />}
    </div>
  );
};

/** Remount on workspace/project change (not on tab change) so no state from the previous
 * project or workspace can leak into the new one. */
const ProjectDetailsPage = () => {
  const { projectId } = useParams();
  const { currentTenant } = useTenant();
  return <ProjectDetails key={`${currentTenant?.id}:${projectId}`} />;
};

export default ProjectDetailsPage;
