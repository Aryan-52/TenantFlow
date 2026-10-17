import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { Task } from '../../api/work';
import type { Milestone } from '../../api/planning';
import { Button } from '../ui/Button';
import { Select } from '../ui/Select';
import { STATUS_LABELS, addDays, dueState, formatDueDate, parseDateKey, toDateKey, todayKey } from '../tasks/taskMeta';
import { taskHref } from '../tasks/taskLinks';

type GroupBy = 'status' | 'assignee' | 'milestone' | 'none';
type Zoom = 'weeks' | 'months';

interface ProjectTimelineProps {
  tenantId: string;
  tasks: Task[];
  milestones: Milestone[];
}

const DAY_PX: Record<Zoom, number> = { weeks: 28, months: 12 };
const SPAN_DAYS: Record<Zoom, number> = { weeks: 42, months: 120 };
const startOfWeek = (d: Date) => addDays(d, -((d.getDay() + 6) % 7));
const daysBetween = (a: Date, b: Date) => Math.round((b.getTime() - a.getTime()) / 86_400_000);

/**
 * Tasks on a time axis: a bar runs from the planned start to the due date (a dot when only
 * one of them is set). Nothing is inferred: tasks without dates are listed as unscheduled.
 */
export const ProjectTimeline = ({ tenantId, tasks, milestones }: ProjectTimelineProps) => {
  const [zoom, setZoom] = useState<Zoom>('weeks');
  const [groupBy, setGroupBy] = useState<GroupBy>('status');
  const [start, setStart] = useState(() => startOfWeek(addDays(new Date(), -7)));

  const span = SPAN_DAYS[zoom];
  const dayPx = DAY_PX[zoom];
  const end = addDays(start, span - 1);
  const startKey = toDateKey(start);
  const endKey = toDateKey(end);
  const today = todayKey();
  const days = useMemo(() => Array.from({ length: span }, (_, i) => addDays(start, i)), [start, span]);

  const scheduled = tasks.filter((t) => t.dueDate || t.startDate);
  const unscheduled = tasks.filter((t) => !t.dueDate && !t.startDate);
  const visible = scheduled.filter((t) => {
    const from = t.startDate ?? t.dueDate!;
    const to = t.dueDate ?? t.startDate!;
    return to >= startKey && from <= endKey;
  });
  const hiddenCount = scheduled.length - visible.length;

  const groups = useMemo(() => {
    const sorted = [...visible].sort((a, b) => (a.startDate ?? a.dueDate!).localeCompare(b.startDate ?? b.dueDate!));
    if (groupBy === 'none') return [{ key: 'all', label: '', tasks: sorted }];
    const label = (t: Task) =>
      groupBy === 'status' ? STATUS_LABELS[t.status]
        : groupBy === 'assignee' ? t.assigneeName ?? 'Unassigned'
          : milestones.find((m) => m.id === t.milestoneId)?.name ?? 'No milestone';
    const map = new Map<string, Task[]>();
    sorted.forEach((t) => map.set(label(t), [...(map.get(label(t)) ?? []), t]));
    return [...map.entries()].map(([k, v]) => ({ key: k, label: k, tasks: v }));
  }, [visible, groupBy, milestones]);

  const x = (key: string) => daysBetween(start, parseDateKey(key)) * dayPx;
  const shift = (dir: number) => setStart((s) => addDays(s, dir * Math.round(span / 2)));
  const width = span * dayPx;
  const todayX = today >= startKey && today <= endKey ? x(today) + dayPx / 2 : null;

  const bar = (t: Task) => {
    const from = t.startDate ?? t.dueDate!;
    const to = t.dueDate ?? t.startDate!;
    const left = Math.max(0, x(from));
    const right = Math.min(width, x(to) + dayPx);
    const point = from === to && !(t.startDate && t.dueDate);
    const blocked = (t.openBlockers ?? 0) > 0;
    const state = dueState(t);
    const cls = ['timeline-bar', `is-${t.status === 'IN_PROGRESS' ? 'progress' : t.status.toLowerCase()}`, state === 'overdue' ? 'is-overdue' : '', blocked ? 'is-blocked' : '', point ? 'is-point' : '']
      .filter(Boolean).join(' ');
    const describe = `${t.title}: ${t.startDate ? `starts ${formatDueDate(t.startDate)}, ` : ''}${t.dueDate ? `due ${formatDueDate(t.dueDate)}` : 'no due date'}, ${STATUS_LABELS[t.status]}${blocked ? `, blocked by ${t.openBlockers} task${t.openBlockers === 1 ? '' : 's'}` : ''}`;
    return (
      <Link to={taskHref(tenantId, t)} className={cls} style={{ left: point ? x(from) + dayPx / 2 - 6 : left, width: point ? undefined : Math.max(dayPx, right - left) }} aria-label={describe} title={describe}>
        {!point && <span className="truncate">{t.title}</span>}
      </Link>
    );
  };

  return (
    <div>
      <div className="toolbar">
        <div className="cal-nav">
          <Button variant="ghost" className="btn-icon" onClick={() => shift(-1)} aria-label="Earlier"><ChevronLeft size={16} /></Button>
          <span className="cal-title" aria-live="polite">{formatDueDate(startKey)} – {formatDueDate(endKey)}</span>
          <Button variant="ghost" className="btn-icon" onClick={() => shift(1)} aria-label="Later"><ChevronRight size={16} /></Button>
          <Button variant="secondary" size="sm" onClick={() => setStart(startOfWeek(addDays(new Date(), -7)))}>Today</Button>
        </div>
        <div className="toolbar-grow" />
        <Select aria-label="Group by" value={groupBy} onChange={(e) => setGroupBy(e.target.value as GroupBy)}
          options={[
            { value: 'status', label: 'Group: status' },
            { value: 'assignee', label: 'Group: assignee' },
            { value: 'milestone', label: 'Group: milestone' },
            { value: 'none', label: 'No grouping' },
          ]} />
        <div className="segmented" role="group" aria-label="Zoom">
          <button type="button" aria-pressed={zoom === 'weeks'} className={zoom === 'weeks' ? 'active' : ''} onClick={() => setZoom('weeks')}>Weeks</button>
          <button type="button" aria-pressed={zoom === 'months'} className={zoom === 'months' ? 'active' : ''} onClick={() => setZoom('months')}>Months</button>
        </div>
      </div>

      <div className="timeline" role="region" aria-label="Project timeline" tabIndex={0}>
        <div className="timeline-grid" style={{ width: `calc(var(--timeline-label) + ${width}px)` }}>
          <div className="timeline-header">
            <div className="timeline-label text-xs text-muted">Task</div>
            <div className="timeline-scale" aria-hidden="true">
              {days.map((d) => {
                const monthStart = d.getDate() === 1;
                const weekend = d.getDay() === 0 || d.getDay() === 6;
                const showLabel = zoom === 'weeks' ? true : d.getDay() === 1 || monthStart;
                return (
                  <div key={d.getTime()} className={`timeline-day ${weekend ? 'is-weekend' : ''} ${monthStart ? 'is-month-start' : ''}`} style={{ width: dayPx }}>
                    {showLabel ? (monthStart ? d.toLocaleDateString(undefined, { month: 'short' }) : zoom === 'weeks' ? d.getDate() : '') : ''}
                  </div>
                );
              })}
            </div>
          </div>

          <div className="timeline-row">
            <div className="timeline-label text-xs font-semibold">Milestones</div>
            <div className="timeline-track" style={{ width }}>
              {milestones.filter((m) => m.targetDate >= startKey && m.targetDate <= endKey).map((m) => (
                <Link
                  key={m.id}
                  to="../milestones"
                  relative="path"
                  className={`timeline-milestone ${m.status === 'DONE' ? 'is-done' : ''}`}
                  style={{ left: x(m.targetDate) + dayPx / 2 }}
                  aria-label={`Milestone ${m.name}, ${formatDueDate(m.targetDate)}, ${m.status === 'DONE' ? 'done' : 'open'}`}
                  title={`${m.name} · ${formatDueDate(m.targetDate)}`}
                />
              ))}
              {todayX !== null && <span className="timeline-today" style={{ left: todayX }} aria-hidden="true" />}
            </div>
          </div>

          {groups.map((g) => (
            <div key={g.key} role="group" aria-label={g.label || 'Tasks'}>
              {g.label && (
                <div className="timeline-row timeline-group-row">
                  <div className="timeline-label">{g.label} · {g.tasks.length}</div>
                  <div className="timeline-track" style={{ width, height: '1.75rem' }} />
                </div>
              )}
              {g.tasks.map((t) => (
                <div className="timeline-row" key={t.id}>
                  <div className="timeline-label">
                    <span className={`status-dot is-${t.status.toLowerCase()}`} aria-hidden="true" />
                    <Link to={taskHref(tenantId, t)} tabIndex={-1}>{t.title}</Link>
                  </div>
                  <div className="timeline-track" style={{ width }}>
                    {bar(t)}
                    {todayX !== null && <span className="timeline-today" style={{ left: todayX }} aria-hidden="true" />}
                  </div>
                </div>
              ))}
            </div>
          ))}
          {visible.length === 0 && (
            <p className="dash-empty" style={{ padding: 'var(--space-4)' }}>No dated task falls in this range.</p>
          )}
        </div>
      </div>

      <div className="timeline-legend" aria-hidden="true">
        <span><i style={{ background: 'color-mix(in srgb, var(--status-todo) 30%, transparent)' }} /> To do</span>
        <span><i style={{ background: 'color-mix(in srgb, var(--status-progress) 35%, transparent)' }} /> In progress</span>
        <span><i style={{ background: 'color-mix(in srgb, var(--status-done) 35%, transparent)' }} /> Done</span>
        <span><i style={{ backgroundImage: 'repeating-linear-gradient(135deg, transparent 0 3px, var(--status-blocked) 3px 5px)' }} /> Blocked</span>
        <span><i style={{ background: 'var(--danger-color)', width: 2 }} /> Today</span>
      </div>
      {hiddenCount > 0 && <p className="text-xs text-muted mt-2">{hiddenCount} dated task{hiddenCount === 1 ? ' is' : 's are'} outside this range.</p>}

      {unscheduled.length > 0 && (
        <section className="panel timeline-unscheduled" aria-labelledby="unscheduled">
          <div className="panel-head">
            <h2 id="unscheduled" className="section-heading">Not scheduled <span className="count-pill">{unscheduled.length}</span></h2>
            <span className="text-xs text-muted">Add a start or due date to place them on the timeline</span>
          </div>
          <ul className="plain-list">
            {unscheduled.slice(0, 20).map((t) => (
              <li key={t.id}><Link to={taskHref(tenantId, t)}>{t.title}</Link><span className="text-xs text-muted">{STATUS_LABELS[t.status]}</span></li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
};
