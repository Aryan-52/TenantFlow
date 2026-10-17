import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, Info, RefreshCw } from 'lucide-react';
import { analyticsApi, type Analytics, type ProjectHealth } from '../../api/analytics';
import { timeAgo } from '../../api/activity';
import { apiError, type TaskFocus } from '../../api/work';
import { PRIORITY_LABELS, PROJECT_STATUS_LABELS, toDateKey } from '../tasks/taskMeta';
import { BarList, StackedBars, TrendChart, type Series } from '../charts/Charts';
import { StatusBreakdown } from '../ui/ProgressRing';
import { Skeleton } from '../ui/Skeleton';
import { ErrorState } from '../ui/ErrorState';
import { Button } from '../ui/Button';
import { EmptyState } from '../ui/EmptyState';

const RANGES = [
  { key: '4w', label: '4 weeks', days: 27 },
  { key: '8w', label: '8 weeks', days: 55 },
  { key: '12w', label: '12 weeks', days: 83 },
  { key: '6m', label: '6 months', days: 181 },
] as const;
type RangeKey = (typeof RANGES)[number]['key'];

const TREND_SERIES: Series[] = [
  { key: 'created', label: 'Created', color: 'var(--chart-1)' },
  { key: 'completed', label: 'Completed', color: 'var(--chart-3)' },
  { key: 'due', label: 'Due', color: 'var(--chart-2)' },
];
const OVERDUE_SERIES: Series[] = [{ key: 'overdueOpen', label: 'Open and overdue', color: 'var(--chart-2)' }];
const WORKLOAD_SERIES: Series[] = [
  { key: 'todo', label: 'To do', color: 'var(--chart-1)' },
  { key: 'inProgress', label: 'In progress', color: 'var(--chart-2)' },
];

const pct = (v: number | null) => (v == null ? '—' : `${Math.round(v * 100)}%`);
const shortDate = (iso: string) => new Date(`${iso}T00:00:00`).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });

/** Signals that need attention, each with its reason. Shared by the dashboard. */
export const HealthList = ({ tenantId, health, limit }: { tenantId: string; health: ProjectHealth[]; limit?: number }) => {
  const flagged = health.filter((h) => h.signals.length > 0).slice(0, limit ?? health.length);
  if (flagged.length === 0) return <p className="dash-empty">No project needs attention right now.</p>;
  return (
    <ul className="health-list">
      {flagged.map((h) => (
        <li key={h.projectId}>
          <Link to={`/tenants/${tenantId}/projects/${h.projectId}`} className="health-row">
            <span className="health-name">
              {h.name}
              <span className="health-meta">{h.doneTasks}/{h.totalTasks} done · updated {timeAgo(h.lastActivityAt)}</span>
            </span>
            <span className="health-signals">
              {h.signals.map((s) => (
                <span key={s.code} className={`signal is-${s.severity}`}>
                  {s.severity === 'high' && <AlertTriangle size={11} aria-hidden="true" />}
                  {s.message}
                </span>
              ))}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  );
};

/** How each health signal is defined: shown wherever signals are. */
export const HealthCriteria = ({ criteria }: { criteria: Analytics['criteria'] }) => (
  <details className="criteria">
    <summary><Info size={12} aria-hidden="true" /> How are projects flagged?</summary>
    <ul>
      <li><b>Overdue</b>: an open task is past its due date.</li>
      <li><b>High priority</b>: an urgent task is open, or a high-priority task is overdue.</li>
      <li><b>Due soon</b>: at least {criteria.dueSoonMin} open tasks are due within {criteria.dueSoonDays} days.</li>
      <li><b>No recent updates</b>: open work, but neither the project nor any task changed for {criteria.staleDays} days.</li>
      <li><b>Workload concentration</b>: with at least {criteria.concentrationMinOpen} open tasks, one person holds {criteria.concentrationSharePercent}% or more.</li>
      <li><b>Unassigned</b>: at least {criteria.unassignedMin} open tasks have no assignee.</li>
    </ul>
    <p className="text-xs text-muted">Only active and on-hold projects are checked. These are prompts for a conversation, not performance ratings.</p>
  </details>
);

interface AnalyticsViewProps {
  tenantId: string;
  /** Project analytics; omit for the whole workspace. */
  projectId?: string;
}

/** Workspace or project analytics from live data, with a date range for the time series. */
export const AnalyticsView = ({ tenantId, projectId }: AnalyticsViewProps) => {
  const [range, setRange] = useState<RangeKey>('8w');
  const [data, setData] = useState<Analytics | null>(null);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  const [loadedKey, setLoadedKey] = useState('');
  const requestKey = `${tenantId}:${projectId ?? ''}:${range}:${retry}`;
  const loading = loadedKey !== requestKey;

  useEffect(() => {
    const controller = new AbortController();
    const days = RANGES.find((r) => r.key === range)!.days;
    const today = new Date();
    const from = new Date(today.getFullYear(), today.getMonth(), today.getDate() - days);
    analyticsApi
      .get(tenantId, { projectId, from: toDateKey(from), to: toDateKey(today) }, controller.signal)
      .then((d) => {
        setData(d);
        setError('');
      })
      .catch((err) => {
        if (!controller.signal.aborted) setError(apiError(err, 'Could not load analytics.').message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadedKey(requestKey);
      });
    return () => controller.abort();
  }, [tenantId, projectId, range, requestKey]);

  const t = tenantId;
  const focus = (f: TaskFocus) => `/tenants/${t}/tasks?focus=${f}`;

  const toolbar = (
    <div className="analytics-toolbar">
      <div className="segmented" role="group" aria-label="Date range">
        {RANGES.map((r) => (
          <button key={r.key} type="button" className={range === r.key ? 'active' : ''} aria-pressed={range === r.key} onClick={() => setRange(r.key)}>
            {r.label}
          </button>
        ))}
      </div>
      <Button variant="ghost" size="sm" onClick={() => setRetry((n) => n + 1)} aria-label="Refresh analytics" data-tooltip="Refresh">
        <RefreshCw size={14} />
      </Button>
    </div>
  );

  if (!data) {
    return (
      <div>
        {toolbar}
        {error ? (
          <div className="panel">
            <ErrorState message={error} />
            <Button variant="secondary" onClick={() => setRetry((n) => n + 1)}>Try again</Button>
          </div>
        ) : (
          <div className="analytics-grid" aria-busy="true" aria-label="Loading analytics">
            {Array.from({ length: 4 }, (_, i) => <Skeleton key={i} style={{ height: '14rem', borderRadius: 'var(--radius-xl)' }} />)}
          </div>
        )}
      </div>
    );
  }

  const { tasks, delivery } = data;
  if (tasks.total === 0) {
    return (
      <div>
        {toolbar}
        <EmptyState icon={<Info size={22} />} title="No tasks yet" description="Analytics appear once there are tasks to measure. Create a task to get started." />
      </div>
    );
  }

  return (
    <div aria-busy={loading}>
      {toolbar}
      <ErrorState message={error} />

      <div className="kpi-row">
        <Link to={focus('open')} className="kpi"><span className="kpi-label">Open tasks</span><span className="kpi-value">{tasks.open}</span><span className="kpi-meta">of {tasks.total} total</span></Link>
        <Link to={focus('overdue')} className={`kpi ${tasks.overdue > 0 ? 'is-danger' : ''}`.trim()}><span className="kpi-label">Overdue</span><span className="kpi-value">{tasks.overdue}</span><span className="kpi-meta">open and past due</span></Link>
        <Link to={focus('due-soon')} className="kpi"><span className="kpi-label">Due in 7 days</span><span className="kpi-value">{tasks.dueSoon}</span><span className="kpi-meta">open</span></Link>
        <div className="kpi"><span className="kpi-label">Completion</span><span className="kpi-value">{pct(tasks.completionRate)}</span><span className="kpi-meta">of all tasks done</span></div>
        <div className="kpi"><span className="kpi-label">On time</span><span className="kpi-value">{pct(delivery.onTimeRate)}</span>
          <span className="kpi-meta">{delivery.onTime + delivery.late > 0 ? `${delivery.onTime} of ${delivery.onTime + delivery.late} with a due date` : 'no dated completions in range'}</span></div>
        <div className="kpi"><span className="kpi-label">Avg. time to complete</span><span className="kpi-value">{delivery.averageDaysToComplete == null ? '—' : `${delivery.averageDaysToComplete}d`}</span><span className="kpi-meta">{delivery.completed} completed in range</span></div>
      </div>

      <div className="analytics-grid">
        <section className="panel analytics-wide" aria-labelledby="an-trend">
          <div className="panel-head">
            <h2 id="an-trend" className="section-heading">Tasks over time</h2>
            <span className="text-xs text-muted">Per {data.range.bucket === 'WEEK' ? 'week' : 'day'} · {shortDate(data.range.from)} – {shortDate(data.range.to)}</span>
          </div>
          <TrendChart data={data.trend.map((p) => ({ ...p }))} series={TREND_SERIES} label="Tasks created, completed and due over time" formatDate={shortDate} />
          {(delivery.untrackedDone > 0 || delivery.completionTrackedSince) && (
            <p className="analytics-note">
              <Info size={12} aria-hidden="true" />
              {delivery.completionTrackedSince
                ? `Completion times are recorded since ${new Date(delivery.completionTrackedSince).toLocaleDateString()}. `
                : 'No completion times have been recorded yet. '}
              {delivery.untrackedDone > 0 && `${delivery.untrackedDone} task${delivery.untrackedDone === 1 ? ' was' : 's were'} completed earlier without a recorded time and are not in the completion figures.`}
            </p>
          )}
        </section>

        <section className="panel" aria-labelledby="an-status">
          <div className="panel-head"><h2 id="an-status" className="section-heading">Status</h2></div>
          <StatusBreakdown todo={tasks.byStatus.TODO} inProgress={tasks.byStatus.IN_PROGRESS} done={tasks.byStatus.DONE} />
          <div className="mt-6">
            <h3 className="analytics-subhead">Open tasks by priority</h3>
            <BarList
              label="Open tasks by priority"
              items={(['URGENT', 'HIGH', 'MEDIUM', 'LOW'] as const).map((p) => ({ key: p, label: PRIORITY_LABELS[p], value: tasks.openByPriority[p] ?? 0 }))}
            />
          </div>
          <p className="text-xs text-muted mt-3">
            {tasks.unassignedOpen} open unassigned · {tasks.openWithoutDueDate} open without a due date
          </p>
        </section>

        <section className="panel" aria-labelledby="an-workload">
          <div className="panel-head">
            <h2 id="an-workload" className="section-heading">Open work by person</h2>
            <Link to={`/tenants/${t}/members`} className="card-link">Members</Link>
          </div>
          {data.workload.length === 0 ? (
            <p className="dash-empty">No open tasks.</p>
          ) : (
            <StackedBars
              label="Open tasks per assignee by status"
              series={WORKLOAD_SERIES}
              rows={data.workload.map((w) => ({
                key: w.userId ?? 'unassigned',
                label: w.name ?? 'Unassigned',
                values: { todo: w.todo, inProgress: w.inProgress },
                note: w.overdue > 0 ? `${w.overdue} overdue` : undefined,
              }))}
            />
          )}
          <p className="text-xs text-muted mt-3">Task counts show where work sits, not how productive anyone is.</p>
        </section>

        {data.projects && (
          <section className="panel" aria-labelledby="an-projects">
            <div className="panel-head">
              <h2 id="an-projects" className="section-heading">Projects</h2>
              <Link to={`/tenants/${t}/projects`} className="card-link">All projects</Link>
            </div>
            <BarList
              label="Projects by status"
              items={(['ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED'] as const).map((s) => ({
                key: s,
                label: PROJECT_STATUS_LABELS[s],
                value: data.projects!.byStatus[s] ?? 0,
                href: `/tenants/${t}/projects?status=${s}`,
              }))}
            />
            <div className="mt-6">
              <h3 className="analytics-subhead">Progress of non-archived projects</h3>
              <BarList label="Projects by progress" items={data.projects.progress.map((b) => ({ key: b.label, label: b.label, value: b.count }))} />
            </div>
          </section>
        )}

        <section className="panel analytics-wide" aria-labelledby="an-overdue">
          <div className="panel-head">
            <h2 id="an-overdue" className="section-heading">Overdue work over time</h2>
            <span className="text-xs text-muted">Open tasks past their due date at the end of each {data.range.bucket === 'WEEK' ? 'week' : 'day'}</span>
          </div>
          <TrendChart data={data.trend.map((p) => ({ date: p.date, overdueOpen: p.overdueOpen ?? 0 }))} series={OVERDUE_SERIES} label="Open overdue tasks over time" formatDate={shortDate} />
          <p className="analytics-note">
            <Info size={12} aria-hidden="true" />
            Rebuilt from due dates and recorded completion times. Tasks completed before completion times were recorded are left out rather than guessed.
          </p>
        </section>

        {data.flow && (
          <section className="panel" aria-labelledby="an-flow">
            <div className="panel-head"><h2 id="an-flow" className="section-heading">Flow</h2></div>
            <dl className="stat-row">
              <div><dt>Blocked open tasks</dt><dd className={data.flow.blockedOpen ? 'text-warning' : ''}>{data.flow.blockedOpen}</dd></div>
              <div><dt>Average age of open tasks</dt><dd>{data.flow.averageOpenAgeDays == null ? '—' : `${data.flow.averageOpenAgeDays}d`}</dd></div>
            </dl>
            <h3 className="analytics-subhead">Open tasks by age</h3>
            <BarList label="Open tasks by days since creation" items={data.flow.openAge.map((b) => ({ key: b.label, label: b.label, value: b.count }))} />
            <p className="text-xs text-muted mt-3">Time spent in each status is not shown: status history was not recorded in a form that can be measured reliably.</p>
          </section>
        )}

        {data.milestones && (
          <section className="panel" aria-labelledby="an-milestones">
            <div className="panel-head"><h2 id="an-milestones" className="section-heading">Milestones</h2></div>
            <dl className="stat-row">
              <div><dt>Done</dt><dd>{data.milestones.done}/{data.milestones.total}</dd></div>
              <div><dt>Overdue</dt><dd className={data.milestones.overdue ? 'text-danger' : ''}>{data.milestones.overdue}</dd></div>
              <div><dt>Completed in range</dt><dd>{data.milestones.completedInRange}</dd></div>
            </dl>
            {data.milestones.upcoming.length === 0 ? (
              <p className="dash-empty">No open milestone due in the next 30 days.</p>
            ) : (
              <ul className="plain-list">
                {data.milestones.upcoming.map((m) => (
                  <li key={m.id}>
                    <Link to={`/tenants/${t}/projects/${m.projectId}/milestones`}>{m.name}</Link>
                    <span className="text-xs text-muted">{m.projectName} · {shortDate(m.targetDate)}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}

        <section className="panel" aria-labelledby="an-health">
          <div className="panel-head"><h2 id="an-health" className="section-heading">Needs attention</h2></div>
          <HealthList tenantId={t} health={data.health} />
          <HealthCriteria criteria={data.criteria} />
        </section>
      </div>
    </div>
  );
};
