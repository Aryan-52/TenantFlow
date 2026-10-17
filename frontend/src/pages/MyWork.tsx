import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { CheckCircle, Inbox, RefreshCw } from 'lucide-react';
import { myWorkApi, type MyWork as MyWorkData } from '../api/planning';
import { apiError, type Task } from '../api/work';
import { useTenant } from '../contexts/TenantContext';
import { useToast } from '../contexts/ToastContext';
import { useLoad } from '../hooks/useLoad';
import { useWorkspaceData } from '../hooks/useWorkspaceData';
import { PageHeader } from '../components/ui/PageHeader';
import { SearchInput } from '../components/ui/SearchInput';
import { Select } from '../components/ui/Select';
import { Button } from '../components/ui/Button';
import { ErrorState } from '../components/ui/ErrorState';
import { EmptyState } from '../components/ui/EmptyState';
import { Skeleton } from '../components/ui/Skeleton';
import { TaskRow } from '../components/tasks/TaskRow';
import { taskHref } from '../components/tasks/taskLinks';
import { PRIORITY_LABELS, PRIORITY_RANK, addDays, parseDateKey, toDateKey } from '../components/tasks/taskMeta';
import { timeAgo } from '../api/activity';

type GroupBy = 'urgency' | 'project' | 'priority';

interface Section {
  key: string;
  title: string;
  hint?: string;
  tasks: Task[];
}

const UPCOMING_DAYS = 7;

/** Highest priority first, then soonest due, then oldest. */
const byFocus = (a: Task, b: Task) =>
  PRIORITY_RANK[b.priority] - PRIORITY_RANK[a.priority] ||
  (a.dueDate ?? '9999').localeCompare(b.dueDate ?? '9999') ||
  a.createdAt.localeCompare(b.createdAt);

/**
 * The signed-in person's daily view of one workspace. Groups come from real due dates,
 * the person's own "Today" pins and dependency data; nothing is scheduled for them.
 */
const MyWork = () => {
  const { currentTenant } = useTenant();
  const tenantId = currentTenant?.id ?? '';
  const toast = useToast();
  const { data, setData, error, loading, reload } = useLoad<MyWorkData>(
    (signal) => myWorkApi.get(tenantId, signal),
    [tenantId],
    'Could not load your work.',
  );
  const { members, projects } = useWorkspaceData(tenantId);
  const [search, setSearch] = useState('');
  const [projectId, setProjectId] = useState('');
  const [groupBy, setGroupBy] = useState<GroupBy>('urgency');

  const replace = (task: Task) =>
    setData((d) => {
      if (!d) return d;
      // Reassigned to someone else or completed: it leaves the open list.
      const stillMine = task.status !== 'DONE' && d.open.some((t) => t.id === task.id) && task.assigneeId === d.open.find((t) => t.id === task.id)?.assigneeId;
      const open = stillMine ? d.open.map((t) => (t.id === task.id ? { ...task, openBlockers: task.openBlockers ?? t.openBlockers } : t)) : d.open.filter((t) => t.id !== task.id);
      const completed = task.status === 'DONE' ? [task, ...d.completed.filter((t) => t.id !== task.id)] : d.completed.filter((t) => t.id !== task.id);
      return { ...d, open, completed };
    });

  const togglePin = async (task: Task) => {
    if (!data) return;
    const pinned = data.focusTaskIds.includes(task.id);
    try {
      if (pinned) await myWorkApi.unfocus(tenantId, task.id);
      else await myWorkApi.focus(tenantId, task.id);
      setData({ ...data, focusTaskIds: pinned ? data.focusTaskIds.filter((id) => id !== task.id) : [...data.focusTaskIds, task.id] });
    } catch (err) {
      toast.error(apiError(err, 'Could not update Today').message);
    }
  };

  const filtered = useMemo(() => {
    if (!data) return [];
    const q = search.trim().toLowerCase();
    return data.open
      .filter((t) => !projectId || t.projectId === projectId)
      .filter((t) => !q || t.title.toLowerCase().includes(q) || t.projectName.toLowerCase().includes(q));
  }, [data, search, projectId]);

  const sections = useMemo<Section[]>(() => {
    if (!data) return [];
    const today = data.today;
    const soon = toDateKey(addDays(parseDateKey(today), UPCOMING_DAYS));
    if (groupBy === 'project') {
      const map = new Map<string, Task[]>();
      filtered.forEach((t) => map.set(t.projectName, [...(map.get(t.projectName) ?? []), t]));
      return [...map.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([name, tasks]) => ({ key: name, title: name, tasks: tasks.sort(byFocus) }));
    }
    if (groupBy === 'priority') {
      return (['URGENT', 'HIGH', 'MEDIUM', 'LOW'] as const).map((p) => ({
        key: p,
        title: PRIORITY_LABELS[p],
        tasks: filtered.filter((t) => t.priority === p).sort(byFocus),
      }));
    }
    const pinned = new Set(data.focusTaskIds);
    const blocked = (t: Task) => (t.openBlockers ?? 0) > 0;
    const groups: Section[] = [
      { key: 'overdue', title: 'Overdue', hint: 'Past the due date', tasks: [] },
      { key: 'today', title: 'Today', hint: 'Due today, or added to Today', tasks: [] },
      { key: 'upcoming', title: 'Upcoming', hint: `Due in the next ${UPCOMING_DAYS} days`, tasks: [] },
      { key: 'blocked', title: 'Waiting and blocked', hint: 'Waiting on unfinished tasks', tasks: [] },
      { key: 'later', title: 'Later', hint: 'Due after next week', tasks: [] },
      { key: 'none', title: 'No due date', tasks: [] },
    ];
    const at = (k: string) => groups.find((g) => g.key === k)!.tasks;
    for (const t of filtered) {
      if (blocked(t) && !pinned.has(t.id)) at('blocked').push(t);
      else if (t.dueDate && t.dueDate < today) at('overdue').push(t);
      else if (pinned.has(t.id) || t.dueDate === today) at('today').push(t);
      else if (!t.dueDate) at('none').push(t);
      else if (t.dueDate <= soon) at('upcoming').push(t);
      else at('later').push(t);
    }
    groups.forEach((g) => g.tasks.sort(byFocus));
    return groups;
  }, [data, filtered, groupBy]);

  // "Focus": what to do next - overdue or due today, unblocked, most important first.
  const focus = useMemo(() => {
    if (!data) return [];
    const pinned = new Set(data.focusTaskIds);
    return filtered
      .filter((t) => (t.openBlockers ?? 0) === 0 && (pinned.has(t.id) || (t.dueDate !== null && t.dueDate <= data.today)))
      .sort(byFocus)
      .slice(0, 3);
  }, [data, filtered]);

  if (!currentTenant) return null;

  return (
    <div>
      <PageHeader
        title="My Work"
        subtitle={data ? `${data.open.length} open task${data.open.length === 1 ? '' : 's'} assigned to you in ${currentTenant.name}` : `Your tasks in ${currentTenant.name}`}
        actions={
          <Button variant="secondary" onClick={reload} aria-label="Refresh my work" disabled={loading}>
            <RefreshCw size={15} /> Refresh
          </Button>
        }
      />

      {error && (
        <div className="flex items-center gap-3 mb-4">
          <div className="flex-1"><ErrorState message={error} /></div>
          <Button variant="secondary" onClick={reload}>Try again</Button>
        </div>
      )}

      {!data ? (
        !error && (
          <div className="flex flex-col gap-3" aria-busy="true" aria-label="Loading your work">
            <Skeleton style={{ height: '5rem' }} />
            <Skeleton style={{ height: '14rem' }} />
          </div>
        )
      ) : (
        <>
          {focus.length > 0 && (
            <section className="focus-strip" aria-labelledby="focus-title">
              <h2 id="focus-title" className="focus-strip-title">Focus</h2>
              <ol className="focus-list">
                {focus.map((t, i) => (
                  <li key={t.id}>
                    <span className="focus-rank" aria-hidden="true">{i + 1}</span>
                    <Link to={taskHref(tenantId, t)} className="focus-link">
                      <span className="truncate">{t.title}</span>
                      <span className="focus-why">
                        {t.dueDate && t.dueDate < data.today ? 'Overdue' : t.dueDate === data.today ? 'Due today' : 'On Today'} · {PRIORITY_LABELS[t.priority]}
                      </span>
                    </Link>
                  </li>
                ))}
              </ol>
            </section>
          )}

          <div className="toolbar" role="search" aria-label="Filter my work">
            <SearchInput value={search} onChange={setSearch} placeholder="Search my tasks" className="toolbar-grow" />
            <Select
              aria-label="Project"
              value={projectId}
              onChange={(e) => setProjectId(e.target.value)}
              options={[{ value: '', label: 'All projects' }, ...projects.map((p) => ({ value: p.id, label: p.name }))]}
            />
            <Select
              aria-label="Group by"
              value={groupBy}
              onChange={(e) => setGroupBy(e.target.value as GroupBy)}
              options={[
                { value: 'urgency', label: 'Group: urgency' },
                { value: 'project', label: 'Group: project' },
                { value: 'priority', label: 'Group: priority' },
              ]}
            />
          </div>

          {data.open.length === 0 ? (
            <EmptyState icon={<Inbox size={22} />} title="Nothing assigned to you" description="Tasks assigned to you in this workspace show up here." />
          ) : filtered.length === 0 ? (
            <EmptyState variant="plain" title="No task matches these filters" description="Try another search or project." action={<Button variant="secondary" onClick={() => { setSearch(''); setProjectId(''); }}>Clear filters</Button>} />
          ) : (
            <div className="work-sections">
              {sections.filter((s) => s.tasks.length > 0).map((s) => (
                <section key={s.key} className={`work-section is-${s.key}`} aria-labelledby={`sec-${s.key}`}>
                  <header className="work-section-head">
                    <h2 id={`sec-${s.key}`} className="work-section-title">
                      {s.title} <span className="count-pill">{s.tasks.length}</span>
                    </h2>
                    {s.hint && <span className="text-xs text-muted">{s.hint}</span>}
                  </header>
                  <ul className="work-list">
                    {s.tasks.map((t) => (
                      <TaskRow
                        key={t.id}
                        tenantId={tenantId}
                        task={t}
                        onChange={replace}
                        members={members}
                        showProject={groupBy !== 'project'}
                        pinned={data.focusTaskIds.includes(t.id)}
                        onTogglePin={togglePin}
                      />
                    ))}
                  </ul>
                </section>
              ))}
            </div>
          )}

          <section className="work-section is-completed" aria-labelledby="sec-completed">
            <header className="work-section-head">
              <h2 id="sec-completed" className="work-section-title">
                <CheckCircle size={15} aria-hidden="true" /> Completed recently <span className="count-pill">{data.completed.length}</span>
              </h2>
              <span className="text-xs text-muted">Last {data.completedDays} days</span>
            </header>
            {data.completed.length === 0 ? (
              <p className="dash-empty">Nothing completed in the last {data.completedDays} days.</p>
            ) : (
              <ul className="done-list">
                {data.completed.slice(0, 15).map((t) => (
                  <li key={t.id}>
                    <Link to={taskHref(tenantId, t)}>{t.title}</Link>
                    <span className="text-xs text-muted">{t.projectName}{t.completedAt ? ` · ${timeAgo(t.completedAt)}` : ''}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </div>
  );
};

export default MyWork;
