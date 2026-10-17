import { useEffect, useMemo, useState } from 'react';
import { useParams, useSearchParams, Link, NavLink, Navigate, useNavigate } from 'react-router-dom';
import { ActivityFeed } from '../components/activity/ActivityFeed';
import { AttachmentsPanel } from '../components/attachments/AttachmentsPanel';
import client from '../api/client';
import { apiError, projectApi, taskApi, type Project, type Task } from '../api/work';
import { useTenant } from '../contexts/TenantContext';
import { useAuth } from '../contexts/AuthContext';
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
import { useBulkTaskActions } from '../components/tasks/useBulkTaskActions';
import { Avatar } from '../components/ui/Avatar';
import { Breadcrumbs } from '../components/ui/PageHeader';
import { ProgressRing, StatusBreakdown } from '../components/ui/ProgressRing';
import { Dropdown, DropdownItem } from '../components/ui/Dropdown';
import { TasksIllustration } from '../components/illustrations/Illustrations';
import { initials, tintStyle } from '../components/ui/visual';
import { timeAgo } from '../api/activity';
import { useToast } from '../contexts/ToastContext';
import { AnalyticsView, HealthCriteria } from '../components/analytics/AnalyticsView';
import { analyticsApi, type Analytics } from '../api/analytics';
import { attachmentApi } from '../api/attachments';
import { milestoneApi, type Milestone } from '../api/planning';
import { ProjectTimeline } from '../components/planning/ProjectTimeline';
import { MilestonesPanel } from '../components/planning/MilestonesPanel';
import { formatDueDate, todayKey } from '../components/tasks/taskMeta';
import {
  Trash2,
  Pencil,
  Plus,
  LayoutDashboard,
  ListTodo,
  Columns3,
  CalendarDays,
  MoreHorizontal,
  Lock,
  Paperclip,
  History,
  CalendarClock,
  BarChart3,
  AlertTriangle,
  Flag,
  SquareChartGantt,
} from 'lucide-react';

const VIEWS = [
  { key: '', label: 'Overview', icon: LayoutDashboard },
  { key: 'tasks', label: 'Tasks', icon: ListTodo },
  { key: 'kanban', label: 'Kanban', icon: Columns3 },
  { key: 'calendar', label: 'Calendar', icon: CalendarDays },
  { key: 'timeline', label: 'Timeline', icon: SquareChartGantt },
  { key: 'milestones', label: 'Milestones', icon: Flag },
  { key: 'files', label: 'Files', icon: Paperclip },
  { key: 'activity', label: 'Activity', icon: History },
  { key: 'analytics', label: 'Analytics', icon: BarChart3 },
] as const;

const ProjectDetails = () => {
  const { projectId, view = '' } = useParams();
  const { currentTenant } = useTenant();
  const { user } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

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
  const bulkActions = useBulkTaskActions(tenantId, setTasks);
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedTaskId = searchParams.get('task');

  // Deep link (?task=:id, used by notifications): open that task once tasks are loaded,
  // then drop the parameter so closing the dialog does not reopen it.
  useEffect(() => {
    if (!requestedTaskId || loading) return;
    const task = tasks.find((t) => t.id === requestedTaskId);
    if (task) {
      setActiveTask(task);
      setTaskDefaults(undefined);
      setTaskModalOpen(true);
    } else {
      setActionError('That task no longer exists or was moved to another project.');
    }
    setSearchParams((params) => {
      params.delete('task');
      return params;
    }, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestedTaskId, loading]);

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

  const [fileCount, setFileCount] = useState<number | null>(null);
  const [milestones, setMilestones] = useState<Milestone[]>([]);
  useEffect(() => {
    if (!tenantId || !projectId) return;
    const controller = new AbortController();
    milestoneApi
      .list(tenantId, projectId, controller.signal)
      .then(setMilestones)
      .catch(() => undefined); // The Milestones tab offers its own retry by reloading.
    return () => controller.abort();
  }, [tenantId, projectId]);
  const [health, setHealth] = useState<{ signals: Analytics['health'][number]['signals']; criteria: Analytics['criteria'] } | null>(null);
  useEffect(() => {
    if (!tenantId || !projectId) return;
    let cancelled = false;
    attachmentApi
      .list(tenantId, projectId, { size: 1 })
      .then((page) => !cancelled && setFileCount(page.totalElements))
      .catch(() => undefined); // The Files tab shows its own errors.
    analyticsApi
      .get(tenantId, { projectId })
      .then((a) => !cancelled && setHealth({ signals: a.health[0]?.signals ?? [], criteria: a.criteria }))
      .catch(() => undefined); // Optional insight: the overview works without it.
    return () => {
      cancelled = true;
    };
  }, [tenantId, projectId]);

  const handleDeleteProject = async () => {
    if (!currentTenant || !project) return;
    setDeleteProjectLoading(true);
    try {
      await projectApi.remove(currentTenant.id, project.id);
      toast.success(`Project "${project.name}" deleted`);
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
      <div className="flex flex-col gap-6" aria-busy="true" aria-label="Loading project">
        <Skeleton style={{ height: '1rem', width: '12rem' }} />
        <div className="flex items-center gap-4">
          <Skeleton style={{ height: '3.5rem', width: '3.5rem', borderRadius: 'var(--radius-lg)' }} />
          <Skeleton style={{ height: '2.25rem', width: '40%' }} />
        </div>
        <Skeleton style={{ height: '2.75rem' }} />
        <div className="project-overview">
          <Skeleton style={{ height: '16rem', borderRadius: 'var(--radius-xl)' }} />
          <Skeleton style={{ height: '16rem', borderRadius: 'var(--radius-xl)' }} />
        </div>
      </div>
    );
  }

  if (error) return <ErrorState message={error} />;
  if (!project || !currentTenant) return <ErrorState message="Project not found" />;

  const addTaskButton = (
    <Button onClick={() => openCreateTask()}>
      <Plus size={16} /> Add task
    </Button>
  );

  return (
    <div>
      <Breadcrumbs
        items={[
          { label: 'Projects', to: `/tenants/${currentTenant.id}/projects` },
          { label: project.name },
        ]}
      />

      <header className="project-header">
        <span className="project-mark project-mark-xl" style={tintStyle(project.id)} aria-hidden="true">
          {initials(project.name)}
        </span>
        <div className="project-header-text">
          <div className="flex items-center gap-3 flex-wrap">
            <h1 className="page-title">{project.name}</h1>
            <ProjectStatusBadge status={project.status} />
          </div>
          <div className="project-header-meta">
            <span className="flex items-center gap-2">
              {project.ownerName ? (
                <>
                  <Avatar name={project.ownerName} size="xs" />
                  Led by {project.ownerName}
                </>
              ) : (
                'No owner yet'
              )}
            </span>
            <span aria-hidden="true">·</span>
            <span>
              {stats.total} task{stats.total === 1 ? '' : 's'}
            </span>
            <span aria-hidden="true">·</span>
            <span>{stats.percent}% complete</span>
            {(project.startDate || project.targetDate) && (
              <>
                <span aria-hidden="true">·</span>
                <span className={project.targetDate && project.targetDate < todayKey() && stats.done < stats.total ? 'text-danger' : ''}>
                  {project.startDate ? formatDueDate(project.startDate) : 'No start'} → {project.targetDate ? `target ${formatDueDate(project.targetDate)}` : 'no target'}
                </span>
              </>
            )}
            <span aria-hidden="true">·</span>
            <span title={new Date(project.updatedAt).toLocaleString()}>Updated {timeAgo(project.updatedAt)}</span>
          </div>
        </div>
        <div className="page-header-actions">
          {canManage && (
            <Button variant="secondary" onClick={() => setEditProjectOpen(true)}>
              <Pencil size={15} /> Edit
            </Button>
          )}
          {addTaskButton}
          {canManage && (
            <Dropdown
              align="right"
              trigger={({ open, toggle }) => (
                <button type="button" className="icon-button" onClick={toggle} aria-haspopup="menu" aria-expanded={open} aria-label="More project actions" data-tooltip="More">
                  <MoreHorizontal size={18} />
                </button>
              )}
            >
              <DropdownItem icon={<Trash2 size={15} />} danger onClick={() => setDeleteProjectOpen(true)}>
                Delete project
              </DropdownItem>
            </Dropdown>
          )}
        </div>
      </header>

      <nav className="tabs project-tabs" aria-label="Project views">
        {VIEWS.map(({ key, label, icon: Icon }) => (
          <NavLink key={key || 'overview'} to={key ? `${basePath}/${key}` : basePath} end className={({ isActive }) => `tab-link ${isActive ? 'active' : ''}`.trim()}>
            <Icon size={15} aria-hidden="true" /> {label}
            {key === 'tasks' && stats.total > 0 && <span className="tab-count">{stats.total}</span>}
            {key === 'files' && !!fileCount && <span className="tab-count">{fileCount}</span>}
            {key === 'milestones' && milestones.length > 0 && <span className="tab-count">{milestones.length}</span>}
          </NavLink>
        ))}
      </nav>

      <ErrorState message={actionError || statusChange.error} />

      <ProjectFormModal
        isOpen={editProjectOpen}
        onClose={() => setEditProjectOpen(false)}
        tenantId={currentTenant.id}
        project={project}
        members={members}
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
        milestones={milestones}
        canDelete={canManage}
        onSaved={handleTaskSaved}
        onDeleted={(id) => setTasks((prev) => prev.filter((t) => t.id !== id))}
      />

      {view === '' && (
        <div className="project-overview">
          <div className="dash-column">
            <section className="card" aria-labelledby="project-about">
              <div className="card-header">
                <h2 id="project-about" className="section-title">About this project</h2>
              </div>
              <div className="card-body">
                <p className={`project-description ${project.description ? '' : 'is-empty'}`.trim()}>
                  {project.description || (canManage ? 'No description yet. Add one so the team knows what this project is for.' : 'No description yet.')}
                </p>

                <div className="detail-groups">
                  <div className="detail-group">
                    <div className="detail-group-head">
                      <span className="eyebrow">Details</span>
                      {canManage && (
                        <button type="button" className="card-link link-button" onClick={() => setEditProjectOpen(true)}>
                          <Pencil size={12} aria-hidden="true" /> Edit
                        </button>
                      )}
                    </div>
                    <dl className="detail-list">
                      <div>
                        <dt>Status</dt>
                        <dd>
                          <ProjectStatusBadge status={project.status} />
                        </dd>
                      </div>
                      <div>
                        <dt>Owner</dt>
                        <dd>
                          {project.ownerName ? (
                            <span className="assignee">
                              <Avatar name={project.ownerName} size="xs" />
                              {project.ownerName}
                            </span>
                          ) : (
                            <span className="text-muted">Unassigned</span>
                          )}
                        </dd>
                      </div>
                    </dl>
                  </div>

                  <div className="detail-group is-readonly">
                    <div className="detail-group-head">
                      <span className="eyebrow">Record</span>
                      <span className="readonly-hint">
                        <Lock size={11} aria-hidden="true" /> Read-only
                      </span>
                    </div>
                    <dl className="detail-list">
                      <div>
                        <dt>Created by</dt>
                        <dd>
                          {project.createdByName ? (
                            <span className="assignee">
                              <Avatar name={project.createdByName} size="xs" />
                              {project.createdByName}
                            </span>
                          ) : (
                            // No creator on record: the project predates creator tracking, or the
                            // creator's account was deleted. Never guess (e.g. from the owner).
                            <span className="text-muted" title="The creator was not recorded for this project (created before creator tracking, or the account was deleted).">
                              Unknown
                            </span>
                          )}
                        </dd>
                      </div>
                      <div>
                        <dt>Created</dt>
                        <dd>{new Date(project.createdAt).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })}</dd>
                      </div>
                      <div>
                        <dt>Last updated</dt>
                        <dd title={new Date(project.updatedAt).toLocaleString()}>{timeAgo(project.updatedAt)}</dd>
                      </div>
                    </dl>
                  </div>
                </div>
              </div>
            </section>

            <section className="card" aria-labelledby="project-upcoming">
              <div className="card-header">
                <h2 id="project-upcoming" className="section-title flex items-center gap-2">
                  <span className="section-icon" aria-hidden="true"><CalendarClock size={16} /></span>
                  Due soon & overdue
                </h2>
                <Link to={`${basePath}/calendar`} className="card-link">
                  Open calendar
                </Link>
              </div>
              <div className="card-body">
                {tasks.length === 0 ? (
                  <EmptyState
                    variant="plain"
                    illustration={<TasksIllustration />}
                    title="No tasks yet"
                    description="Add the first task to get this project moving."
                    action={addTaskButton}
                  />
                ) : upcoming.length === 0 ? (
                  <p className="dash-empty">Nothing due in the next two weeks.</p>
                ) : (
                  <ul className="flex flex-col gap-2">
                    {upcoming.map((t) => (
                      <li key={t.id}>
                        <button type="button" className={`cal-day-item due-${dueState(t)}`} onClick={() => openTask(t)}>
                          <span className="font-medium truncate">{t.title}</span>
                          <span className="flex items-center gap-3 flex-shrink-0">
                            {t.assigneeName && <Avatar name={t.assigneeName} size="xs" withTitle />}
                            <PriorityBadge priority={t.priority} />
                            <DueDateLabel task={t} />
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </section>

          </div>

          <div className="dash-column">
            <section className="card" aria-labelledby="project-progress">
              <div className="card-header">
                <h2 id="project-progress" className="section-title">Progress</h2>
              </div>
              <div className="card-body">
                <div className="project-progress">
                  <ProgressRing percent={stats.percent} label={`${stats.percent}% of tasks done`} />
                  <div className="project-progress-text">
                    <div className="text-lg font-semibold">
                      {stats.done} of {stats.total} done
                    </div>
                    <div className={`text-sm ${stats.overdue ? 'text-danger' : 'text-muted'}`}>
                      {stats.overdue ? `${stats.overdue} overdue` : stats.total ? 'Nothing overdue' : 'No tasks yet'}
                    </div>
                  </div>
                </div>
                <div className="mt-6">
                  <StatusBreakdown todo={stats.todo} inProgress={stats.inProgress} done={stats.done} />
                </div>
                <div className="project-quick-links">
                  <Link to={`${basePath}/kanban`} className="card-link">
                    <Columns3 size={14} aria-hidden="true" /> Board
                  </Link>
                  <Link to={`${basePath}/tasks`} className="card-link">
                    <ListTodo size={14} aria-hidden="true" /> Task list
                  </Link>
                  <Link to={`${basePath}/calendar`} className="card-link">
                    <CalendarDays size={14} aria-hidden="true" /> Calendar
                  </Link>
                </div>
              </div>
            </section>

            <section className="card" aria-labelledby="project-milestones">
              <div className="card-header">
                <h2 id="project-milestones" className="section-title flex items-center gap-2">
                  <span className="section-icon" aria-hidden="true"><Flag size={16} /></span>
                  Milestones
                </h2>
                <Link to={`${basePath}/milestones`} className="card-link">All milestones</Link>
              </div>
              <div className="card-body">
                {milestones.filter((m) => m.status === 'OPEN').length === 0 ? (
                  <p className="dash-empty">{milestones.length ? 'All milestones are complete.' : 'No milestones yet.'}</p>
                ) : (
                  <ul className="plain-list">
                    {milestones.filter((m) => m.status === 'OPEN').slice(0, 4).map((m) => (
                      <li key={m.id}>
                        <span className="truncate">{m.name}</span>
                        <span className={`text-xs ${m.targetDate < todayKey() ? 'text-danger' : 'text-muted'}`}>
                          {m.targetDate < todayKey() ? 'Overdue · ' : ''}{formatDueDate(m.targetDate)}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </section>

            <section className="card" aria-labelledby="project-attention">
              <div className="card-header">
                <h2 id="project-attention" className="section-title flex items-center gap-2">
                  <span className="section-icon" aria-hidden="true"><AlertTriangle size={16} /></span>
                  Needs attention
                </h2>
                <Link to={`${basePath}/analytics`} className="card-link">Analytics</Link>
              </div>
              <div className="card-body">
                {!health ? (
                  <Skeleton style={{ height: '2rem' }} />
                ) : health.signals.length === 0 ? (
                  <p className="dash-empty">Nothing flagged for this project.</p>
                ) : (
                  <div className="health-signals">
                    {health.signals.map((sig) => (
                      <span key={sig.code} className={`signal is-${sig.severity}`}>{sig.message}</span>
                    ))}
                  </div>
                )}
                {health && <HealthCriteria criteria={health.criteria} />}
              </div>
            </section>
          </div>
        </div>
      )}

      {view === 'tasks' && (
        <TaskTable
          tasks={tasks}
          members={members}
          onOpen={openTask}
          onStatusChange={statusChange.changeStatus}
          emptyAction={addTaskButton}
          bulk={{ ...bulkActions, remove: canManage ? bulkActions.remove : undefined }}
          currentUserId={user?.id}
          viewsKey={`tf.taskViews.${currentTenant.id}`}
        />
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

      {view === 'files' && (
        <section className="panel" aria-labelledby="project-files">
          <div className="panel-head">
            <h2 id="project-files" className="section-heading">Files</h2>
            <span className="text-xs text-muted">Project documents and every task's attachments</span>
          </div>
          <AttachmentsPanel tenantId={currentTenant.id} projectId={project.id} canManage={canManage} onCountChange={setFileCount} />
        </section>
      )}

      {view === 'activity' && (
        <section className="panel" aria-labelledby="project-activity">
          <div className="panel-head">
            <h2 id="project-activity" className="section-heading">Activity</h2>
          </div>
          <ActivityFeed tenantId={currentTenant.id} projectId={project.id} pageSize={20} />
        </section>
      )}

      {view === 'analytics' && <AnalyticsView tenantId={currentTenant.id} projectId={project.id} />}

      {view === 'timeline' && <ProjectTimeline tenantId={currentTenant.id} tasks={tasks} milestones={milestones} />}

      {view === 'milestones' && (
        <MilestonesPanel tenantId={currentTenant.id} projectId={project.id} milestones={milestones} onChange={setMilestones} canDelete={canManage} />
      )}
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
