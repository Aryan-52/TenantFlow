import { useMemo, useState } from 'react';
import { CalendarX2, ChevronLeft, ChevronRight, Plus } from 'lucide-react';
import type { Task } from '../../api/work';
import { Button } from '../ui/Button';
import { PriorityBadge, StatusBadge } from '../ui/Badge';
import { addDays, compareByDueThenPriority, dueState, toDateKey, todayKey } from './taskMeta';

type Mode = 'month' | 'week' | 'day';

interface TaskCalendarProps {
  tasks: Task[];
  onOpen: (task: Task) => void;
  /** Start creating a task due on the given "YYYY-MM-DD" date. */
  onCreate: (dueDate: string) => void;
}

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const MONTH_CHIP_LIMIT = 3;

/** Monday-based start of the week containing d. */
const startOfWeek = (d: Date) => addDays(d, -((d.getDay() + 6) % 7));

const TaskChip = ({ task, onOpen }: { task: Task; onOpen: (t: Task) => void }) => (
  <button
    type="button"
    className={`cal-task due-${dueState(task)}`}
    onClick={(e) => {
      e.stopPropagation();
      onOpen(task);
    }}
    title={task.title}
  >
    <span className="cal-task-title">{task.title}</span>
  </button>
);

/** Focused month / week / day calendar of task due dates. */
export const TaskCalendar = ({ tasks, onOpen, onCreate }: TaskCalendarProps) => {
  const [mode, setMode] = useState<Mode>('month');
  const [cursor, setCursor] = useState(() => new Date());
  const today = todayKey();

  const { byDate, undated } = useMemo(() => {
    const map = new Map<string, Task[]>();
    const none: Task[] = [];
    tasks.forEach((t) => {
      if (!t.dueDate) {
        none.push(t);
        return;
      }
      const list = map.get(t.dueDate) ?? [];
      list.push(t);
      map.set(t.dueDate, list);
    });
    map.forEach((list) => list.sort(compareByDueThenPriority));
    return { byDate: map, undated: none };
  }, [tasks]);

  const days = useMemo(() => {
    if (mode === 'day') return [cursor];
    if (mode === 'week') {
      const start = startOfWeek(cursor);
      return Array.from({ length: 7 }, (_, i) => addDays(start, i));
    }
    const first = new Date(cursor.getFullYear(), cursor.getMonth(), 1);
    const start = startOfWeek(first);
    const last = new Date(cursor.getFullYear(), cursor.getMonth() + 1, 0);
    const end = addDays(startOfWeek(last), 6);
    const result: Date[] = [];
    for (let d = start; d <= end; d = addDays(d, 1)) result.push(d);
    return result;
  }, [mode, cursor]);

  const shift = (direction: 1 | -1) => {
    if (mode === 'month') setCursor(new Date(cursor.getFullYear(), cursor.getMonth() + direction, 1));
    else setCursor(addDays(cursor, direction * (mode === 'week' ? 7 : 1)));
  };

  const openDay = (d: Date) => {
    setCursor(d);
    setMode('day');
  };

  const title =
    mode === 'month'
      ? cursor.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
      : mode === 'week'
        ? `${days[0].toLocaleDateString(undefined, { month: 'short', day: 'numeric' })} – ${days[6].toLocaleDateString(undefined, {
            month: 'short',
            day: 'numeric',
            year: 'numeric',
          })}`
        : cursor.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });

  const dayKey = toDateKey(cursor);
  const dayTasks = byDate.get(dayKey) ?? [];

  return (
    <div className="flex flex-col gap-4">
      <div className="card">
        <div className="cal-toolbar">
          <div className="flex items-center gap-2">
            <Button variant="secondary" size="sm" className="btn-icon" onClick={() => shift(-1)} aria-label={`Previous ${mode}`}>
              <ChevronLeft size={16} />
            </Button>
            <Button variant="secondary" size="sm" className="btn-icon" onClick={() => shift(1)} aria-label={`Next ${mode}`}>
              <ChevronRight size={16} />
            </Button>
            <Button variant="secondary" size="sm" onClick={() => setCursor(new Date())}>
              Today
            </Button>
            <h3 className="mb-0 ml-2 cal-title" aria-live="polite">
              {title}
            </h3>
          </div>
          <div className="segmented" role="group" aria-label="Calendar view">
            {(['month', 'week', 'day'] as Mode[]).map((m) => (
              <button key={m} type="button" className={mode === m ? 'active' : ''} aria-pressed={mode === m} onClick={() => setMode(m)}>
                {m.charAt(0).toUpperCase() + m.slice(1)}
              </button>
            ))}
          </div>
        </div>

        {mode === 'day' ? (
          <div className="card-body">
            {dayTasks.length === 0 ? (
              <p className="text-muted text-sm mb-4">No tasks due on this day.</p>
            ) : (
              <ul className="flex flex-col gap-2 mb-4">
                {dayTasks.map((t) => (
                  <li key={t.id}>
                    <button type="button" className={`cal-day-item due-${dueState(t)}`} onClick={() => onOpen(t)}>
                      <span className="font-medium truncate">{t.title}</span>
                      <span className="flex items-center gap-2 flex-shrink-0">
                        <StatusBadge status={t.status} />
                        <PriorityBadge priority={t.priority} />
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            <Button size="sm" variant="secondary" onClick={() => onCreate(dayKey)}>
              <Plus size={14} /> Add task due this day
            </Button>
          </div>
        ) : (
          <div className={`cal-grid cal-${mode}`}>
            {WEEKDAYS.map((w) => (
              <div key={w} className="cal-weekday">
                {w}
              </div>
            ))}
            {days.map((d) => {
              const key = toDateKey(d);
              const list = byDate.get(key) ?? [];
              const outside = mode === 'month' && d.getMonth() !== cursor.getMonth();
              const shown = mode === 'month' ? list.slice(0, MONTH_CHIP_LIMIT) : list;
              return (
                <div key={key} className={`cal-cell ${outside ? 'is-outside' : ''} ${key === today ? 'is-today' : ''}`.trim()}>
                  <div className="cal-cell-header">
                    <button
                      type="button"
                      className="cal-date"
                      onClick={() => openDay(d)}
                      aria-label={`${d.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })}, ${list.length} task${list.length === 1 ? '' : 's'}`}
                    >
                      {d.getDate()}
                    </button>
                    <button
                      type="button"
                      className="cal-add"
                      onClick={() => onCreate(key)}
                      aria-label={`Add task due ${d.toLocaleDateString()}`}
                      title="Add task due this day"
                    >
                      <Plus size={12} />
                    </button>
                  </div>
                  <div className="cal-tasks">
                    {shown.map((t) => (
                      <TaskChip key={t.id} task={t} onOpen={onOpen} />
                    ))}
                    {list.length > shown.length && (
                      <button type="button" className="cal-more" onClick={() => openDay(d)}>
                        +{list.length - shown.length} more
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}

        <div className="cal-legend">
          <span className="cal-legend-item due-overdue">Overdue</span>
          <span className="cal-legend-item due-today">Due today</span>
          <span className="cal-legend-item due-upcoming">Upcoming</span>
          <span className="cal-legend-item due-done">Completed</span>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <h3 className="mb-0 flex items-center gap-2">
            <CalendarX2 size={16} /> No due date
          </h3>
          <span className="text-sm text-muted">{undated.length}</span>
        </div>
        <div className="card-body">
          {undated.length === 0 ? (
            <p className="text-sm text-muted">Every task in this project has a due date.</p>
          ) : (
            <>
              <p className="text-sm text-muted mb-3">Open a task to schedule it.</p>
              <div className="flex flex-wrap gap-2">
                {undated.map((t) => (
                  <button key={t.id} type="button" className={`cal-task cal-task-pill due-${t.status === 'DONE' ? 'done' : 'none'}`} onClick={() => onOpen(t)}>
                    <span className="cal-task-title">{t.title}</span>
                  </button>
                ))}
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
};

