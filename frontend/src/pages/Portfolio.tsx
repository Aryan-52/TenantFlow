import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, Columns3, FolderKanban, LayoutGrid, RefreshCw, Table2 } from 'lucide-react';
import { planningApi, type PortfolioRow } from '../api/planning';
import { timeAgo } from '../api/activity';
import type { ProjectStatus } from '../api/work';
import { useTenant } from '../contexts/TenantContext';
import { useLoad } from '../hooks/useLoad';
import { PageHeader } from '../components/ui/PageHeader';
import { SearchInput } from '../components/ui/SearchInput';
import { Select } from '../components/ui/Select';
import { Button } from '../components/ui/Button';
import { ErrorState } from '../components/ui/ErrorState';
import { EmptyState } from '../components/ui/EmptyState';
import { TableSkeleton } from '../components/ui/Skeleton';
import { ProjectStatusBadge } from '../components/ui/Badge';
import { Avatar } from '../components/ui/Avatar';
import { Dropdown, DropdownLabel } from '../components/ui/Dropdown';
import { PROJECT_STATUS_LABELS, formatDueDate, todayKey } from '../components/tasks/taskMeta';
import { initials, tintStyle } from '../components/ui/visual';

type View = 'table' | 'cards';
type Sort = 'name' | 'target' | 'progress' | 'overdue' | 'activity' | 'attention';
type Column = 'owner' | 'dates' | 'progress' | 'open' | 'overdue' | 'milestone' | 'activity' | 'health';

const COLUMNS: { key: Column; label: string }[] = [
  { key: 'owner', label: 'Owner' },
  { key: 'dates', label: 'Start / target' },
  { key: 'progress', label: 'Progress' },
  { key: 'open', label: 'Open' },
  { key: 'overdue', label: 'Overdue / blocked' },
  { key: 'milestone', label: 'Next milestone' },
  { key: 'activity', label: 'Last activity' },
  { key: 'health', label: 'Needs attention' },
];
const PREFS_KEY = 'tf.portfolio';
const STATUS_ORDER: ProjectStatus[] = ['ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED'];

interface Prefs {
  view: View;
  hidden: Column[];
}

const readPrefs = (): Prefs => {
  try {
    const p = JSON.parse(localStorage.getItem(PREFS_KEY) ?? '{}');
    return { view: p.view === 'cards' ? 'cards' : 'table', hidden: Array.isArray(p.hidden) ? p.hidden : [] };
  } catch {
    return { view: 'table', hidden: [] };
  }
};

const weight = (r: PortfolioRow) => r.signals.reduce((n, s) => n + (s.severity === 'high' ? 3 : s.severity === 'medium' ? 2 : 1), 0);
const percent = (r: PortfolioRow) => (r.totalTasks ? Math.round((r.doneTasks / r.totalTasks) * 100) : 0);

/** Every project of the workspace at a glance, with explainable health signals. */
const Portfolio = () => {
  const { currentTenant } = useTenant();
  const tenantId = currentTenant?.id ?? '';
  const { data, error, loading, reload } = useLoad<PortfolioRow[]>((s) => planningApi.portfolio(tenantId, s), [tenantId], 'Could not load the portfolio.');
  const [prefs, setPrefs] = useState<Prefs>(readPrefs);
  const [search, setSearch] = useState('');
  const [status, setStatus] = useState<'' | 'OPEN' | ProjectStatus>('OPEN');
  const [owner, setOwner] = useState('');
  const [sort, setSort] = useState<Sort>('attention');
  const [grouped, setGrouped] = useState(true);

  const savePrefs = (next: Prefs) => {
    setPrefs(next);
    try {
      localStorage.setItem(PREFS_KEY, JSON.stringify(next));
    } catch {
      // Remembered for this page only.
    }
  };
  const show = (c: Column) => !prefs.hidden.includes(c);

  const owners = useMemo(() => {
    const m = new Map<string, string>();
    (data ?? []).forEach((r) => r.ownerId && m.set(r.ownerId, r.ownerName ?? 'Unknown'));
    return [...m.entries()].sort((a, b) => a[1].localeCompare(b[1]));
  }, [data]);

  const rows = useMemo(() => {
    const q = search.trim().toLowerCase();
    const list = (data ?? [])
      .filter((r) => !q || r.name.toLowerCase().includes(q) || (r.description ?? '').toLowerCase().includes(q))
      .filter((r) => (status === 'OPEN' ? r.status === 'ACTIVE' || r.status === 'ON_HOLD' : !status || r.status === status))
      .filter((r) => !owner || (owner === 'none' ? !r.ownerId : r.ownerId === owner));
    const cmp: Record<Sort, (a: PortfolioRow, b: PortfolioRow) => number> = {
      name: (a, b) => a.name.localeCompare(b.name),
      target: (a, b) => (a.targetDate ?? '9999').localeCompare(b.targetDate ?? '9999'),
      progress: (a, b) => percent(a) - percent(b),
      overdue: (a, b) => b.overdueTasks - a.overdueTasks,
      activity: (a, b) => b.lastActivityAt.localeCompare(a.lastActivityAt),
      attention: (a, b) => weight(b) - weight(a) || a.name.localeCompare(b.name),
    };
    return [...list].sort(cmp[sort]);
  }, [data, search, status, owner, sort]);

  const groups = grouped
    ? STATUS_ORDER.map((s) => ({ key: s, label: PROJECT_STATUS_LABELS[s], rows: rows.filter((r) => r.status === s) })).filter((g) => g.rows.length)
    : [{ key: 'all', label: '', rows }];

  const today = todayKey();
  const projectHref = (r: PortfolioRow) => `/tenants/${tenantId}/projects/${r.id}`;
  const atRisk = (data ?? []).filter((r) => r.signals.some((s) => s.severity === 'high')).length;

  if (!currentTenant) return null;

  return (
    <div>
      <PageHeader
        title="Portfolio"
        subtitle={data ? `${data.length} project${data.length === 1 ? '' : 's'} · ${atRisk} with high-severity warnings` : 'All projects in this workspace'}
        actions={
          <>
            <div className="segmented" role="group" aria-label="Layout">
              <button type="button" aria-pressed={prefs.view === 'table'} className={prefs.view === 'table' ? 'active' : ''} onClick={() => savePrefs({ ...prefs, view: 'table' })}>
                <Table2 size={14} aria-hidden="true" /> Table
              </button>
              <button type="button" aria-pressed={prefs.view === 'cards'} className={prefs.view === 'cards' ? 'active' : ''} onClick={() => savePrefs({ ...prefs, view: 'cards' })}>
                <LayoutGrid size={14} aria-hidden="true" /> Cards
              </button>
            </div>
            <Button variant="ghost" className="btn-icon" onClick={reload} aria-label="Refresh portfolio" disabled={loading}>
              <RefreshCw size={15} />
            </Button>
          </>
        }
      />

      <div className="toolbar" role="search" aria-label="Filter projects">
        <SearchInput value={search} onChange={setSearch} placeholder="Search projects" className="toolbar-grow" />
        <Select
          aria-label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value as typeof status)}
          options={[
            { value: 'OPEN', label: 'Active and on hold' },
            { value: '', label: 'All statuses' },
            ...STATUS_ORDER.map((s) => ({ value: s, label: PROJECT_STATUS_LABELS[s] })),
          ]}
        />
        <Select
          aria-label="Owner"
          value={owner}
          onChange={(e) => setOwner(e.target.value)}
          options={[{ value: '', label: 'Any owner' }, { value: 'none', label: 'No owner' }, ...owners.map(([id, name]) => ({ value: id, label: name }))]}
        />
        <Select
          aria-label="Sort"
          value={sort}
          onChange={(e) => setSort(e.target.value as Sort)}
          options={[
            { value: 'attention', label: 'Sort: needs attention' },
            { value: 'name', label: 'Sort: name' },
            { value: 'target', label: 'Sort: target date' },
            { value: 'progress', label: 'Sort: least progress' },
            { value: 'overdue', label: 'Sort: most overdue' },
            { value: 'activity', label: 'Sort: recent activity' },
          ]}
        />
        <label className="check-inline">
          <input type="checkbox" checked={grouped} onChange={(e) => setGrouped(e.target.checked)} /> Group by status
        </label>
        {prefs.view === 'table' && (
          <Dropdown
            align="right"
            trigger={({ open, toggle }) => (
              <Button variant="secondary" onClick={toggle} aria-expanded={open} aria-haspopup="true">
                <Columns3 size={15} /> Columns
              </Button>
            )}
          >
            <DropdownLabel>Show columns</DropdownLabel>
            <div className="column-picker" onClick={(e) => e.stopPropagation()}>
              {COLUMNS.map((c) => (
                <label key={c.key} className="check-inline">
                  <input
                    type="checkbox"
                    checked={show(c.key)}
                    onChange={() => savePrefs({ ...prefs, hidden: show(c.key) ? [...prefs.hidden, c.key] : prefs.hidden.filter((h) => h !== c.key) })}
                  />
                  {c.label}
                </label>
              ))}
            </div>
          </Dropdown>
        )}
      </div>

      <ErrorState message={error} />

      {!data ? (
        !error && <TableSkeleton rows={5} cols={6} />
      ) : data.length === 0 ? (
        <EmptyState icon={<FolderKanban size={22} />} title="No projects yet" description="Create a project to see it here."
          action={<Link className="btn btn-primary" to={`/tenants/${tenantId}/projects?new=1`}>New project</Link>} />
      ) : rows.length === 0 ? (
        <EmptyState variant="plain" title="No project matches these filters" description="Change the filters to see more projects." />
      ) : prefs.view === 'cards' ? (
        groups.map((g) => (
          <section key={g.key} className="portfolio-group" aria-label={g.label || 'Projects'}>
            {g.label && <h2 className="group-title">{g.label} <span className="count-pill">{g.rows.length}</span></h2>}
            <ul className="portfolio-cards">
              {g.rows.map((r) => (
                <li key={r.id} className="portfolio-card">
                  <Link to={projectHref(r)} className="portfolio-card-head">
                    <span className="project-mark" style={tintStyle(r.id)} aria-hidden="true">{initials(r.name)}</span>
                    <span className="portfolio-card-name">{r.name}</span>
                    <ProjectStatusBadge status={r.status} />
                  </Link>
                  <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent(r)} aria-label={`${r.name} progress`}>
                    <span className="progress-bar" style={{ width: `${percent(r)}%` }} />
                  </div>
                  <dl className="portfolio-facts">
                    <div><dt>Done</dt><dd>{r.doneTasks}/{r.totalTasks}</dd></div>
                    <div><dt>Overdue</dt><dd className={r.overdueTasks ? 'text-danger' : ''}>{r.overdueTasks}</dd></div>
                    <div><dt>Target</dt><dd className={r.targetDate && r.targetDate < today && r.openTasks ? 'text-danger' : ''}>{r.targetDate ? formatDueDate(r.targetDate) : '—'}</dd></div>
                  </dl>
                  {r.signals.length > 0 && (
                    <ul className="signal-list">
                      {r.signals.slice(0, 3).map((s) => (
                        <li key={s.code} className={`signal is-${s.severity}`}>{s.severity === 'high' && <AlertTriangle size={11} aria-hidden="true" />}{s.message}</li>
                      ))}
                    </ul>
                  )}
                  <div className="portfolio-card-foot">
                    {r.ownerName ? <span className="assignee"><Avatar name={r.ownerName} size="xs" /> {r.ownerName}</span> : <span className="text-muted">No owner</span>}
                    <span className="text-muted">{timeAgo(r.lastActivityAt)}</span>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        ))
      ) : (
        <div className="table-container">
          <table className="table portfolio-table">
            <caption className="sr-only">Projects with progress, dates and health</caption>
            <thead>
              <tr>
                <th scope="col">Project</th>
                {show('owner') && <th scope="col">Owner</th>}
                {show('dates') && <th scope="col">Start / target</th>}
                {show('progress') && <th scope="col">Progress</th>}
                {show('open') && <th scope="col" className="num">Open</th>}
                {show('overdue') && <th scope="col" className="num">Overdue / blocked</th>}
                {show('milestone') && <th scope="col">Next milestone</th>}
                {show('activity') && <th scope="col">Last activity</th>}
                {show('health') && <th scope="col">Needs attention</th>}
              </tr>
            </thead>
            {groups.map((g) => (
              <tbody key={g.key}>
                {g.label && (
                  <tr className="group-row">
                    <th colSpan={1 + COLUMNS.filter((c) => show(c.key)).length} scope="rowgroup">{g.label} · {g.rows.length}</th>
                  </tr>
                )}
                {g.rows.map((r) => (
                  <tr key={r.id}>
                    <th scope="row">
                      <Link to={projectHref(r)} className="cell-link">
                        <span className="project-mark project-mark-sm" style={tintStyle(r.id)} aria-hidden="true">{initials(r.name)}</span>
                        <span className="truncate">{r.name}</span>
                      </Link>
                      {!grouped && <ProjectStatusBadge status={r.status} />}
                    </th>
                    {show('owner') && <td>{r.ownerName ?? <span className="text-muted">—</span>}</td>}
                    {show('dates') && (
                      <td className="nowrap">
                        {r.startDate ? formatDueDate(r.startDate) : '—'} → <span className={r.targetDate && r.targetDate < today && r.openTasks ? 'text-danger' : ''}>{r.targetDate ? formatDueDate(r.targetDate) : '—'}</span>
                      </td>
                    )}
                    {show('progress') && (
                      <td>
                        <div className="inline-progress">
                          <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent(r)} aria-label={`${r.name} progress`}>
                            <span className="progress-bar" style={{ width: `${percent(r)}%` }} />
                          </div>
                          <span className="text-xs text-muted">{r.doneTasks}/{r.totalTasks}</span>
                        </div>
                      </td>
                    )}
                    {show('open') && <td className="num">{r.openTasks}</td>}
                    {show('overdue') && (
                      <td className="num">
                        <span className={r.overdueTasks ? 'text-danger' : ''}>{r.overdueTasks}</span> / <span className={r.blockedTasks ? 'text-warning' : ''}>{r.blockedTasks}</span>
                      </td>
                    )}
                    {show('milestone') && (
                      <td>{r.nextMilestone ? <><span className="truncate">{r.nextMilestone.name}</span> <span className="text-xs text-muted">{formatDueDate(r.nextMilestone.targetDate)}</span></> : <span className="text-muted">—</span>}</td>
                    )}
                    {show('activity') && <td className="text-muted nowrap">{timeAgo(r.lastActivityAt)}</td>}
                    {show('health') && (
                      <td>
                        {r.signals.length === 0 ? (
                          <span className="text-muted">—</span>
                        ) : (
                          <ul className="signal-list">
                            {r.signals.map((s) => (
                              <li key={s.code} className={`signal is-${s.severity}`}>{s.message}</li>
                            ))}
                          </ul>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            ))}
          </table>
        </div>
      )}
      <p className="text-xs text-muted mt-4">
        Warnings are computed from task, milestone and date data for active and on-hold projects. Each one states the condition behind it; there is no hidden score.
      </p>
    </div>
  );
};

export default Portfolio;
