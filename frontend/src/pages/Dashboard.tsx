import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import client from '../api/client';
import { dashboardApi, type DashboardData } from '../api/dashboard';
import { analyticsApi, type Analytics } from '../api/analytics';
import { timeAgo } from '../api/activity';
import { apiError, taskApi, type Task, type TaskFocus } from '../api/work';
import type { Member } from './Members';
import { useTenant, type Tenant } from '../contexts/TenantContext';
import { useAuth } from '../contexts/AuthContext';
import { Button } from '../components/ui/Button';
import { Skeleton } from '../components/ui/Skeleton';
import { ErrorState } from '../components/ui/ErrorState';
import { EmptyState } from '../components/ui/EmptyState';
import { Avatar } from '../components/ui/Avatar';
import { PriorityBadge, ProjectStatusBadge } from '../components/ui/Badge';
import { ActivityFeed } from '../components/activity/ActivityFeed';
import { DueDateLabel } from '../components/tasks/DueDateLabel';
import { ProjectFormModal } from '../components/projects/ProjectFormModal';
import { CreateWorkspaceModal } from '../components/layout/CreateWorkspaceModal';
import { HealthCriteria, HealthList } from '../components/analytics/AnalyticsView';
import { StackedBars } from '../components/charts/Charts';
import { ProjectsIllustration, WorkspaceIllustration } from '../components/illustrations/Illustrations';
import { initials, tintStyle } from '../components/ui/visual';
import { PRIORITY_RANK, addDays, toDateKey } from '../components/tasks/taskMeta';
import { ArrowRight, BarChart3, Flag, FolderKanban, Plus, RefreshCw, Target } from 'lucide-react';
import { formatDueDate } from '../components/tasks/taskMeta';

const tasksView = (tenantId: string, focus: TaskFocus) => `/tenants/${tenantId}/tasks?focus=${focus}`;
const taskLink = (tenantId: string, task: Task) => `/tenants/${tenantId}/projects/${task.projectId}/tasks?task=${task.id}`;

function greeting(now = new Date()): string {
  const h = now.getHours();
  if (h < 5) return 'Good evening';
  if (h < 12) return 'Good morning';
  if (h < 18) return 'Good afternoon';
  return 'Good evening';
}

interface MetricProps {
  to: string;
  label: string;
  value: number | null;
  /** Accessible name: says what the number is and where the link goes. */
  ariaLabel: string;
  meta: string;
  tone?: 'danger' | 'warning';
}

/** One headline number; the whole cell links to the matching filtered view. */
const Metric = ({ to, label, value, ariaLabel, meta, tone }: MetricProps) => (
  <Link to={to} className={`metric ${tone && value ? `is-${tone}` : ''}`.trim()} aria-label={ariaLabel}>
    <span className="metric-label">{label}</span>
    <span className="metric-value">{value ?? '—'}</span>
    <span className="metric-extra">{meta}</span>
  </Link>
);

// ---- My work: the current user's open tasks, grouped by urgency -------------------------

type Group = 'overdue' | 'today' | 'week' | 'later' | 'none';
const GROUPS: { key: Group; label: string }[] = [
  { key: 'overdue', label: 'Overdue' },
  { key: 'today', label: 'Due today' },
  { key: 'week', label: 'This week' },
  { key: 'later', label: 'Upcoming' },
  { key: 'none', label: 'No due date' },
];

function groupTasks(tasks: Task[], today = new Date()): Record<Group, Task[]> {
  const todayKey = toDateKey(today);
  const weekKey = toDateKey(addDays(today, 7));
  const groups: Record<Group, Task[]> = { overdue: [], today: [], week: [], later: [], none: [] };
  for (const t of tasks) {
    if (!t.dueDate) groups.none.push(t);
    else if (t.dueDate < todayKey) groups.overdue.push(t);
    else if (t.dueDate === todayKey) groups.today.push(t);
    else if (t.dueDate <= weekKey) groups.week.push(t);
    else groups.later.push(t);
  }
  // Within a group: highest priority first, then soonest due.
  const byUrgency = (a: Task, b: Task) =>
    PRIORITY_RANK[b.priority] - PRIORITY_RANK[a.priority] || (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999');
  (Object.keys(groups) as Group[]).forEach((g) => groups[g].sort(byUrgency));
  return groups;
}

const MyWork = ({ tenantId, total }: { tenantId: string; total: number }) => {
  const [tasks, setTasks] = useState<Task[] | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    taskApi
      .listForTenant(tenantId, 'mine', 0, 50, controller.signal)
      .then((page) => setTasks(page.items))
      .catch((err) => {
        if (!controller.signal.aborted) setError(apiError(err, 'Could not load your tasks.').message);
      });
    return () => controller.abort();
  }, [tenantId]);

  const groups = useMemo(() => (tasks ? groupTasks(tasks) : null), [tasks]);

  return (
    <section className="panel" aria-labelledby="dash-my-work">
      <div className="panel-head">
        <h2 id="dash-my-work" className="section-heading">My work</h2>
        {total > 0 && (
          <Link to={tasksView(tenantId, 'mine')} className="card-link" aria-label="View all my tasks">
            View all {total} <ArrowRight size={13} aria-hidden="true" />
          </Link>
        )}
      </div>
      <ErrorState message={error} />
      {!groups ? (
        !error && (
          <div className="flex flex-col gap-2" aria-busy="true" aria-label="Loading your tasks">
            <Skeleton style={{ height: '2.75rem' }} />
            <Skeleton style={{ height: '2.75rem' }} />
          </div>
        )
      ) : tasks!.length === 0 ? (
        <p className="dash-empty">Nothing assigned to you is open. Nice.</p>
      ) : (
        <div className="work-groups">
          {GROUPS.filter((g) => groups[g.key].length > 0).map((g) => (
            <div key={g.key} className={`work-group is-${g.key}`}>
              <h3 className="work-group-title">
                {g.label} <span className="work-group-count">{groups[g.key].length}</span>
              </h3>
              <ul className="task-rows">
                {groups[g.key].slice(0, 5).map((task) => (
                  <li key={task.id}>
                    <Link to={taskLink(tenantId, task)} className="task-row">
                      <span className={`task-row-status is-${task.status.toLowerCase()}`} aria-hidden="true" />
                      <span className="task-row-main">
                        <span className="task-row-title">{task.title}</span>
                        <span className="task-row-meta">
                          {task.projectName} · {task.status === 'IN_PROGRESS' ? 'In progress' : 'To do'}
                        </span>
                      </span>
                      <span className="task-row-side">
                        <DueDateLabel task={task} />
                        <PriorityBadge priority={task.priority} />
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
              {groups[g.key].length > 5 && (
                <p className="text-xs text-muted">+{groups[g.key].length - 5} more in this group</p>
              )}
            </div>
          ))}
        </div>
      )}
    </section>
  );
};

const DashboardSkeleton = () => (
  <div aria-busy="true" aria-label="Loading dashboard">
    <Skeleton style={{ height: '6.5rem', borderRadius: 'var(--radius-xl)', marginBottom: 'var(--space-8)' }} />
    <div className="dash-grid">
      <Skeleton style={{ height: '20rem', borderRadius: 'var(--radius-xl)' }} />
      <Skeleton style={{ height: '20rem', borderRadius: 'var(--radius-xl)' }} />
    </div>
  </div>
);

/**
 * One workspace's dashboard. Mounted with key = workspace id, so switching workspace starts
 * from a clean state; in-flight requests of the previous workspace are aborted and their
 * late responses can never be shown.
 */
const WorkspaceDashboard = ({
  tenant,
  reloadToken,
  onSummary,
}: {
  tenant: Tenant;
  reloadToken: number;
  onSummary: (summary: ReactNode) => void;
}) => {
  const [data, setData] = useState<DashboardData | null>(null);
  const [insight, setInsight] = useState<Analytics | null>(null);
  const [insightError, setInsightError] = useState('');
  const [error, setError] = useState('');
  const [retryToken, setRetryToken] = useState(0);
  const [loadedToken, setLoadedToken] = useState<string | null>(null);
  const requestToken = `${reloadToken}:${retryToken}`;
  const loading = loadedToken !== requestToken;

  useEffect(() => {
    const controller = new AbortController();
    dashboardApi
      .get(tenant.id, controller.signal)
      .then((d) => {
        setData(d);
        setError('');
      })
      .catch((err) => {
        if (controller.signal.aborted) return;
        setError(apiError(err, 'Failed to load dashboard data.').message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadedToken(requestToken);
      });
    // Health and workload come from analytics; the dashboard still works if this fails.
    analyticsApi
      .get(tenant.id, {}, controller.signal)
      .then((a) => {
        setInsight(a);
        setInsightError('');
      })
      .catch((err) => {
        if (!controller.signal.aborted) setInsightError(apiError(err, 'Could not load project health.').message);
      });
    return () => controller.abort();
  }, [tenant.id, requestToken]);

  const openTasks = data ? data.todoTasks + data.inProgressTasks : 0;
  // A new workspace starts without the previous workspace's summary.
  useEffect(() => () => onSummary(null), [onSummary]);
  useEffect(() => {
    if (!data) return;
    onSummary(
      <>
        {openTasks === 0
          ? 'No open tasks right now.'
          : `${openTasks} open task${openTasks === 1 ? '' : 's'} across ${data.totalProjects} project${data.totalProjects === 1 ? '' : 's'}`}
        {data.overdueTasks > 0 && <span className="text-danger">{` · ${data.overdueTasks} overdue`}</span>}
        {data.myOpenTasks > 0 && ` · ${data.myOpenTasks} assigned to you`}
      </>,
    );
  }, [data, openTasks, onSummary]);

  if (loading && !data) return <DashboardSkeleton />;

  if (!data) {
    return (
      <div className="panel">
        <ErrorState message={error} />
        <Button variant="secondary" onClick={() => setRetryToken((n) => n + 1)}>
          <RefreshCw size={15} /> Try again
        </Button>
      </div>
    );
  }

  const t = tenant.id;
  const active = insight?.projects?.byStatus.ACTIVE ?? null;

  return (
    <div aria-busy={loading}>
      {/* A failed refresh keeps the last good data on screen. */}
      {error && (
        <div className="flex items-center gap-3 mb-4">
          <div className="flex-1"><ErrorState message={error} /></div>
          <Button variant="secondary" onClick={() => setRetryToken((n) => n + 1)}>Retry</Button>
        </div>
      )}

      <nav className="dash-metrics" aria-label="Workspace at a glance">
        <Metric to={`/tenants/${t}/projects?status=ACTIVE`} label="Active projects" value={active}
          ariaLabel={`Active projects: ${active ?? 'loading'}. View active projects`} meta={`${data.totalProjects} in total`} />
        <Metric to={tasksView(t, 'open')} label="Open tasks" value={openTasks}
          ariaLabel={`Open tasks: ${openTasks}. View open tasks`} meta={`${data.inProgressTasks} in progress`} />
        <Metric to={tasksView(t, 'overdue')} label="Overdue" value={data.overdueTasks} tone="danger"
          ariaLabel={`Overdue tasks: ${data.overdueTasks}. View overdue tasks`} meta="open and past due" />
        <Metric to={tasksView(t, 'due-soon')} label="Due in 7 days" value={data.dueSoonTasks} tone="warning"
          ariaLabel={`Tasks due in the next 7 days: ${data.dueSoonTasks}. View tasks due soon`} meta="still open" />
        <Metric to={tasksView(t, 'mine')} label="Assigned to me" value={data.myOpenTasks}
          ariaLabel={`My open tasks: ${data.myOpenTasks}. View my tasks`} meta="open" />
      </nav>

      {data.totalProjects === 0 ? (
        <EmptyState
          illustration={<ProjectsIllustration />}
          title="No projects yet"
          description="Projects hold your team's tasks, boards, files and calendars. Create the first one to get started."
          action={<Link to={`/tenants/${t}/projects?new=1`} className="btn btn-primary">Create first project</Link>}
        />
      ) : (
        <>
          <div className="dash-grid">
            <MyWork key={requestToken} tenantId={t} total={data.myOpenTasks} />

            <section className="panel" aria-labelledby="dash-health">
              <div className="panel-head">
                <h2 id="dash-health" className="section-heading">Needs attention</h2>
                <Link to={`/tenants/${t}/analytics`} className="card-link">Analytics</Link>
              </div>
              {insightError ? (
                <ErrorState message={insightError} />
              ) : !insight ? (
                <Skeleton style={{ height: '6rem' }} />
              ) : (
                <>
                  <HealthList tenantId={t} health={insight.health} limit={4} />
                  <HealthCriteria criteria={insight.criteria} />
                </>
              )}
            </section>
          </div>

          <div className="dash-grid dash-grid-secondary">
            <section className="dash-section" aria-labelledby="dash-team">
              <div className="section-head">
                <h2 id="dash-team" className="section-heading">Team workload</h2>
                <Link to={`/tenants/${t}/members`} className="card-link">Members</Link>
              </div>
              {!insight ? (
                <Skeleton style={{ height: '6rem' }} />
              ) : insight.workload.length === 0 ? (
                <p className="dash-empty">No open tasks.</p>
              ) : (
                <StackedBars
                  label="Open tasks per person by status"
                  series={[
                    { key: 'todo', label: 'To do', color: 'var(--chart-1)' },
                    { key: 'inProgress', label: 'In progress', color: 'var(--chart-2)' },
                  ]}
                  rows={insight.workload.slice(0, 6).map((w) => ({
                    key: w.userId ?? 'unassigned',
                    label: w.name ?? 'Unassigned',
                    values: { todo: w.todo, inProgress: w.inProgress },
                    note: w.overdue > 0 ? `${w.overdue} overdue` : undefined,
                    href: w.userId ? `/tenants/${t}/members` : tasksView(t, 'open'),
                  }))}
                />
              )}
            </section>

            <section className="dash-section" aria-labelledby="dash-projects">
              <div className="section-head">
                <h2 id="dash-projects" className="section-heading">Recent projects</h2>
                <Link to={`/tenants/${t}/projects`} className="card-link" aria-label="View all projects">View all</Link>
              </div>
              <ul className="project-rows">
                {data.recentProjects.slice(0, 4).map((p) => {
                  const percent = p.totalTasks ? Math.round((p.completedTasks / p.totalTasks) * 100) : 0;
                  return (
                    <li key={p.id}>
                      <Link to={`/tenants/${t}/projects/${p.id}`} className="project-row">
                        <span className="project-mark" style={tintStyle(p.id)} aria-hidden="true">{initials(p.name)}</span>
                        <span className="project-row-main">
                          <span className="flex items-center gap-2" style={{ minWidth: 0 }}>
                            <span className="project-row-title">{p.name}</span>
                            <ProjectStatusBadge status={p.status} />
                          </span>
                          <span className="project-row-progress">
                            <span className={`progress ${percent === 100 ? 'is-complete' : ''}`.trim()} role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent} aria-label={`${p.name} progress`}>
                              <span className="progress-bar" style={{ width: `${percent}%` }} />
                            </span>
                            <span className="project-row-count">{p.completedTasks}/{p.totalTasks} done</span>
                          </span>
                        </span>
                        <span className="project-row-side">
                          {p.ownerName ? <Avatar name={p.ownerName} size="xs" withTitle /> : <span className="avatar-placeholder" title="No owner" />}
                          <span className="project-row-count">{timeAgo(p.updatedAt)}</span>
                        </span>
                      </Link>
                    </li>
                  );
                })}
              </ul>
            </section>
          </div>

          <section className="dash-section" aria-labelledby="dash-dates">
            <div className="section-head">
              <h2 id="dash-dates" className="section-heading">Milestones and deadlines</h2>
              <Link to={`/tenants/${t}/calendar`} className="card-link">Calendar</Link>
            </div>
            {!insight ? (
              <Skeleton style={{ height: '5rem' }} />
            ) : (() => {
              const todayIso = toDateKey(new Date());
              const horizon = toDateKey(addDays(new Date(), 30));
              const targets = insight.health
                .filter((h) => h.targetDate && h.targetDate <= horizon && h.openTasks > 0)
                .map((h) => ({ id: `p-${h.projectId}`, kind: 'target' as const, date: h.targetDate!, name: h.name, projectId: h.projectId, detail: `${h.openTasks} open task${h.openTasks === 1 ? '' : 's'}` }));
              const milestones = (insight.milestones?.upcoming ?? []).map((m) => ({ id: `m-${m.id}`, kind: 'milestone' as const, date: m.targetDate, name: m.name, projectId: m.projectId, detail: m.projectName }));
              const items = [...milestones, ...targets].sort((a, b) => a.date.localeCompare(b.date)).slice(0, 8);
              return items.length === 0 ? (
                <p className="dash-empty">No milestones or project target dates in the next 30 days.</p>
              ) : (
                <ul className="deadline-list">
                  {items.map((i) => (
                    <li key={i.id} className={i.date < todayIso ? 'is-late' : ''}>
                      <span className="deadline-date">{i.date < todayIso ? 'Overdue' : formatDueDate(i.date)}</span>
                      <span className="deadline-icon" aria-hidden="true">{i.kind === 'milestone' ? <Flag size={13} /> : <Target size={13} />}</span>
                      <Link to={`/tenants/${t}/projects/${i.projectId}${i.kind === 'milestone' ? '/milestones' : ''}`} className="deadline-name">
                        {i.kind === 'milestone' ? i.name : `${i.name} target date`}
                      </Link>
                      <span className="text-xs text-muted truncate">{i.detail}</span>
                    </li>
                  ))}
                </ul>
              );
            })()}
          </section>

          <section className="dash-section dash-activity" aria-labelledby="dash-activity">
            <div className="section-head">
              <h2 id="dash-activity" className="section-heading">Activity</h2>
            </div>
            <ActivityFeed tenantId={t} pageSize={6} />
          </section>
        </>
      )}
    </div>
  );
};

const Dashboard = () => {
  const { currentTenant, tenants } = useTenant();
  const { user } = useAuth();
  const [reloadToken, setReloadToken] = useState(0);
  const [summary, setSummary] = useState<ReactNode>(null);
  const [creatingWorkspace, setCreatingWorkspace] = useState(false);
  const [creatingProject, setCreatingProject] = useState(false);
  const [members, setMembers] = useState<Member[]>([]);

  const canManage = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';

  if (!currentTenant && tenants.length === 0) {
    return (
      <div className="max-w-3xl mx-auto">
        <EmptyState
          illustration={<WorkspaceIllustration className="empty-hero-illustration" />}
          title="Welcome to TenantFlow"
          description="You don't belong to any workspaces yet. Create one to start organizing projects and invite your team."
          action={
            <Button onClick={() => setCreatingWorkspace(true)}>
              <Plus size={16} /> Create workspace
            </Button>
          }
        />
        <CreateWorkspaceModal isOpen={creatingWorkspace} onClose={() => setCreatingWorkspace(false)} />
      </div>
    );
  }

  const openNewProject = () => {
    setCreatingProject(true);
    if (currentTenant && members.length === 0) {
      client
        .get<Member[]>(`/api/tenants/${currentTenant.id}/members`)
        .then((res) => setMembers(res.data))
        .catch(() => undefined); // The form still offers "me" and "Unassigned".
    }
  };

  const firstName = user?.name?.trim().split(/\s+/)[0];
  const today = new Date().toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' });

  return (
    <div>
      <header className="hero">
        <div className="hero-text">
          <div className="hero-eyebrow">{currentTenant?.name ? `${currentTenant.name} · ${today}` : today}</div>
          <h1 className="hero-title">{firstName ? `${greeting()}, ${firstName}` : greeting()}</h1>
          <p className="hero-summary">{summary ?? ' '}</p>
        </div>
        {currentTenant && (
          <div className="hero-actions">
            <Button variant="ghost" className="btn-icon" onClick={() => setReloadToken((n) => n + 1)} aria-label="Refresh" data-tooltip="Refresh">
              <RefreshCw size={15} />
            </Button>
            <Link to={`/tenants/${currentTenant.id}/analytics`} className="btn btn-ghost" aria-label="Analytics">
              <BarChart3 size={15} /> <span className="btn-label">Analytics</span>
            </Link>
            {canManage && (
              <Button variant="secondary" onClick={openNewProject} aria-label="New project">
                <FolderKanban size={15} /> <span className="btn-label">New project</span>
              </Button>
            )}
          </div>
        )}
      </header>

      {currentTenant && (
        <>
          <WorkspaceDashboard key={currentTenant.id} tenant={currentTenant} reloadToken={reloadToken} onSummary={setSummary} />
          {canManage && (
            <ProjectFormModal
              isOpen={creatingProject}
              onClose={() => setCreatingProject(false)}
              tenantId={currentTenant.id}
              project={null}
              members={members}
              onSaved={() => setReloadToken((n) => n + 1)}
            />
          )}
        </>
      )}
    </div>
  );
};

export default Dashboard;
