import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight, Flag, Repeat } from 'lucide-react';
import { planningApi, type CalendarData, type CalendarScope, type Milestone } from '../api/planning';
import type { Task } from '../api/work';
import { useTenant } from '../contexts/TenantContext';
import { useLoad } from '../hooks/useLoad';
import { useWorkspaceData } from '../hooks/useWorkspaceData';
import { PageHeader } from '../components/ui/PageHeader';
import { Select } from '../components/ui/Select';
import { Button } from '../components/ui/Button';
import { ErrorState } from '../components/ui/ErrorState';
import { Alert } from '../components/ui/Alert';
import { PriorityBadge, StatusBadge } from '../components/ui/Badge';
import { taskHref } from '../components/tasks/taskLinks';
import { addDays, dueState, parseDateKey, toDateKey, todayKey } from '../components/tasks/taskMeta';

type Mode = 'month' | 'week';
const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const CHIP_LIMIT = 3;
const startOfWeek = (d: Date) => addDays(d, -((d.getDay() + 6) % 7));

/** Days shown for the anchor date: whole weeks covering the month, or one week. */
function visibleDays(anchor: Date, mode: Mode): Date[] {
  const first = mode === 'month' ? startOfWeek(new Date(anchor.getFullYear(), anchor.getMonth(), 1)) : startOfWeek(anchor);
  const count = mode === 'month'
    ? Math.ceil(((new Date(anchor.getFullYear(), anchor.getMonth() + 1, 0).getDate() + ((new Date(anchor.getFullYear(), anchor.getMonth(), 1).getDay() + 6) % 7)) / 7)) * 7
    : 7;
  return Array.from({ length: count }, (_, i) => addDays(first, i));
}

/**
 * Planning calendar for the workspace: tasks by due date and milestones by target date.
 * "My schedule" shows only tasks assigned to me (and the milestones of those projects);
 * "Workspace" shows everything. Dates are calendar days, so they never shift by time zone.
 */
const CalendarPage = () => {
  const { currentTenant } = useTenant();
  const tenantId = currentTenant?.id ?? '';
  const { projects } = useWorkspaceData(tenantId);
  const [mode, setMode] = useState<Mode>('month');
  const [anchor, setAnchor] = useState(() => new Date());
  const [scope, setScope] = useState<CalendarScope>('mine');
  const [projectId, setProjectId] = useState('');
  const [selected, setSelected] = useState(todayKey());

  const days = useMemo(() => visibleDays(anchor, mode), [anchor, mode]);
  const from = toDateKey(days[0]);
  const to = toDateKey(days[days.length - 1]);
  const { data, error, loading, reload } = useLoad<CalendarData>(
    (signal) => planningApi.calendar(tenantId, { from, to, scope, projectId: projectId || undefined }, signal),
    [tenantId, from, to, scope, projectId],
    'Could not load the calendar.',
  );

  const byDay = useMemo(() => {
    const tasks = new Map<string, Task[]>();
    const milestones = new Map<string, Milestone[]>();
    data?.tasks.forEach((t) => t.dueDate && tasks.set(t.dueDate, [...(tasks.get(t.dueDate) ?? []), t]));
    data?.milestones.forEach((m) => milestones.set(m.targetDate, [...(milestones.get(m.targetDate) ?? []), m]));
    return { tasks, milestones };
  }, [data]);

  const move = (dir: number) =>
    setAnchor((a) => (mode === 'month' ? new Date(a.getFullYear(), a.getMonth() + dir, 1) : addDays(a, dir * 7)));
  const today = todayKey();
  const title = mode === 'month'
    ? anchor.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
    : `${days[0].toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} – ${days[6].toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })}`;

  const dayTasks = byDay.tasks.get(selected) ?? [];
  const dayMilestones = byDay.milestones.get(selected) ?? [];
  const overdueCount = data?.tasks.filter((t) => dueState(t) === 'overdue').length ?? 0;

  if (!currentTenant) return null;

  return (
    <div>
      <PageHeader
        title="Calendar"
        subtitle={scope === 'mine' ? 'Your tasks by due date, with the milestones of your projects' : 'Every task and milestone in this workspace'}
        actions={
          <div className="segmented" role="group" aria-label="Whose schedule">
            <button type="button" aria-pressed={scope === 'mine'} className={scope === 'mine' ? 'active' : ''} onClick={() => setScope('mine')}>My schedule</button>
            <button type="button" aria-pressed={scope === 'workspace'} className={scope === 'workspace' ? 'active' : ''} onClick={() => setScope('workspace')}>Workspace</button>
          </div>
        }
      />

      <div className="toolbar">
        <div className="cal-nav">
          <Button variant="ghost" className="btn-icon" onClick={() => move(-1)} aria-label={mode === 'month' ? 'Previous month' : 'Previous week'}><ChevronLeft size={16} /></Button>
          <h2 className="cal-title" aria-live="polite">{title}</h2>
          <Button variant="ghost" className="btn-icon" onClick={() => move(1)} aria-label={mode === 'month' ? 'Next month' : 'Next week'}><ChevronRight size={16} /></Button>
          <Button variant="secondary" size="sm" onClick={() => { setAnchor(new Date()); setSelected(today); }}>Today</Button>
        </div>
        <div className="toolbar-grow" />
        <Select
          aria-label="Project"
          value={projectId}
          onChange={(e) => setProjectId(e.target.value)}
          options={[{ value: '', label: 'All projects' }, ...projects.map((p) => ({ value: p.id, label: p.name }))]}
        />
        <div className="segmented" role="group" aria-label="Calendar view">
          <button type="button" aria-pressed={mode === 'month'} className={mode === 'month' ? 'active' : ''} onClick={() => setMode('month')}>Month</button>
          <button type="button" aria-pressed={mode === 'week'} className={mode === 'week' ? 'active' : ''} onClick={() => setMode('week')}>Week</button>
        </div>
      </div>

      {error && (
        <div className="flex items-center gap-3 mb-4">
          <div className="flex-1"><ErrorState message={error} /></div>
          <Button variant="secondary" onClick={reload}>Try again</Button>
        </div>
      )}
      {data?.truncated && <Alert variant="warning">This range has more items than the calendar can show. Narrow it to one project.</Alert>}
      {overdueCount > 0 && <p className="text-sm text-danger mb-2">{overdueCount} overdue task{overdueCount === 1 ? '' : 's'} in this view.</p>}

      <div className="calendar-layout">
        <div className={`plan-cal is-${mode}`} aria-busy={loading} role="grid" aria-label={`${title} calendar`}>
          <div className="plan-cal-head" role="row">
            {WEEKDAYS.map((d) => <div key={d} role="columnheader" className="plan-cal-weekday">{d}</div>)}
          </div>
          <div className="plan-cal-body">
            {Array.from({ length: days.length / 7 }, (_, w) => (
              <div className="plan-cal-week" role="row" key={w}>
                {days.slice(w * 7, w * 7 + 7).map((d) => {
                  const key = toDateKey(d);
                  const tasks = byDay.tasks.get(key) ?? [];
                  const ms = byDay.milestones.get(key) ?? [];
                  const outside = mode === 'month' && d.getMonth() !== anchor.getMonth();
                  const limit = mode === 'week' ? 12 : CHIP_LIMIT;
                  const items = ms.length + tasks.length;
                  return (
                    <div
                      key={key}
                      role="gridcell"
                      aria-selected={selected === key}
                      className={`plan-cal-day ${outside ? 'is-outside' : ''} ${key === today ? 'is-today' : ''} ${selected === key ? 'is-selected' : ''}`.replace(/\s+/g, ' ').trim()}
                    >
                      <button
                        type="button"
                        className="plan-cal-date"
                        onClick={() => setSelected(key)}
                        aria-label={`${d.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })}: ${items} item${items === 1 ? '' : 's'}`}
                      >
                        {d.getDate()}
                      </button>
                      {ms.map((m) => (
                        <Link key={m.id} to={`/tenants/${tenantId}/projects/${m.projectId}/milestones`} className={`cal-milestone ${m.status === 'DONE' ? 'is-done' : ''}`} title={`${m.name} · ${m.projectName}`}>
                          <Flag size={10} aria-hidden="true" /> <span className="truncate">{m.name}</span>
                        </Link>
                      ))}
                      {tasks.slice(0, Math.max(0, limit - ms.length)).map((t) => (
                        <Link key={t.id} to={taskHref(tenantId, t)} className={`cal-task due-${dueState(t)}`} title={`${t.title} · ${t.projectName}`}>
                          <span className="cal-task-title">{t.title}</span>
                          {t.recurrence && <Repeat size={9} aria-label="Recurring" />}
                        </Link>
                      ))}
                      {items > limit && (
                        <button type="button" className="cal-more" onClick={() => setSelected(key)}>+{items - limit} more</button>
                      )}
                    </div>
                  );
                })}
              </div>
            ))}
          </div>
        </div>

        <aside className="panel cal-agenda" aria-labelledby="agenda-title">
          <h2 id="agenda-title" className="section-heading">
            {parseDateKey(selected).toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })}
          </h2>
          {dayMilestones.length === 0 && dayTasks.length === 0 ? (
            <p className="dash-empty">Nothing scheduled.</p>
          ) : (
            <ul className="agenda-list">
              {dayMilestones.map((m) => (
                <li key={m.id}>
                  <Flag size={13} aria-hidden="true" className="text-warning" />
                  <div className="min-w-0">
                    <Link to={`/tenants/${tenantId}/projects/${m.projectId}/milestones`} className="truncate">{m.name}</Link>
                    <div className="text-xs text-muted">Milestone · {m.projectName} · {m.status === 'DONE' ? 'Done' : `${m.doneTasks}/${m.totalTasks} tasks done`}</div>
                  </div>
                </li>
              ))}
              {dayTasks.map((t) => (
                <li key={t.id}>
                  <span className={`status-dot is-${t.status.toLowerCase()}`} aria-hidden="true" />
                  <div className="min-w-0">
                    <Link to={taskHref(tenantId, t)} className="truncate">{t.title}</Link>
                    <div className="text-xs text-muted flex items-center gap-2 flex-wrap">
                      {t.projectName} · {t.assigneeName ?? 'Unassigned'} <StatusBadge status={t.status} /> <PriorityBadge priority={t.priority} />
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </aside>
      </div>
    </div>
  );
};

export default CalendarPage;
