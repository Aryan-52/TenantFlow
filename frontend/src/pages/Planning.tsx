import { useState } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, Info, RefreshCw } from 'lucide-react';
import { planningApi, type MemberLoad, type Workload } from '../api/planning';
import { apiError } from '../api/work';
import { useTenant } from '../contexts/TenantContext';
import { useAuth } from '../contexts/AuthContext';
import { useToast } from '../contexts/ToastContext';
import { useLoad } from '../hooks/useLoad';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { ErrorState } from '../components/ui/ErrorState';
import { TableSkeleton } from '../components/ui/Skeleton';
import { Avatar } from '../components/ui/Avatar';
import { RoleBadge } from '../components/ui/Badge';
import { DataTable } from '../components/charts/Charts';
import { formatDueDate } from '../components/tasks/taskMeta';

const WEEK_OPTIONS = [4, 6, 8];

/** Shade of a cell: relative to capacity when it is set, otherwise to the busiest week. */
const level = (count: number, capacity: number | null, max: number) => {
  if (count === 0) return 0;
  if (capacity !== null && capacity > 0) return count > capacity ? 4 : count >= capacity * 0.75 ? 3 : 2;
  return Math.min(3, Math.ceil((count / Math.max(1, max)) * 3));
};

const CapacityEditor = ({ member, canEdit, onSave }: { member: MemberLoad; canEdit: boolean; onSave: (v: number | null) => Promise<void> }) => {
  const [value, setValue] = useState(member.weeklyCapacity?.toString() ?? '');
  const [saving, setSaving] = useState(false);
  if (!canEdit) return <span>{member.weeklyCapacity ?? <span className="text-muted">—</span>}</span>;
  const dirty = value !== (member.weeklyCapacity?.toString() ?? '');
  return (
    <form
      className="capacity-form"
      onSubmit={async (e) => {
        e.preventDefault();
        setSaving(true);
        await onSave(value === '' ? null : Number(value));
        setSaving(false);
      }}
    >
      <input
        type="number"
        min={0}
        max={200}
        inputMode="numeric"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        aria-label={`Weekly capacity of ${member.name} in tasks`}
        placeholder="—"
      />
      {dirty && <Button type="submit" size="sm" variant="secondary" isLoading={saving}>Save</Button>}
    </form>
  );
};

/** Where the workspace's open work sits, per person and per week of due dates. */
const Planning = () => {
  const { currentTenant } = useTenant();
  const { user } = useAuth();
  const toast = useToast();
  const tenantId = currentTenant?.id ?? '';
  const [weeks, setWeeks] = useState(6);
  const { data, setData, error, loading, reload } = useLoad<Workload>((s) => planningApi.workload(tenantId, weeks, s), [tenantId, weeks], 'Could not load workload.');
  const isAdmin = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';

  const saveCapacity = async (m: MemberLoad, v: number | null) => {
    if (!m.userId) return;
    try {
      const next = await planningApi.setCapacity(tenantId, m.userId, v);
      // The server answers with a 6-week view; reload for the selected horizon.
      if (weeks === next.weeks) setData(next);
      else reload();
      toast.success(`Capacity of ${m.name} saved`);
    } catch (err) {
      toast.error(apiError(err, 'Could not save the capacity').message);
    }
  };

  if (!currentTenant) return null;
  const max = data ? Math.max(1, ...data.members.flatMap((m) => m.weeks.map((w) => w.due))) : 1;
  const rows: MemberLoad[] = data ? [...data.members, ...(data.unassigned.open > 0 ? [data.unassigned] : [])] : [];
  const tasksFor = (focus: string) => `/tenants/${tenantId}/tasks?focus=${focus}`;

  return (
    <div>
      <PageHeader
        title="Planning"
        subtitle="Open work per person, by the week it is due. Capacity is counted in tasks per week."
        actions={
          <>
            <div className="segmented" role="group" aria-label="Weeks ahead">
              {WEEK_OPTIONS.map((w) => (
                <button key={w} type="button" aria-pressed={weeks === w} className={weeks === w ? 'active' : ''} onClick={() => setWeeks(w)}>
                  {w} weeks
                </button>
              ))}
            </div>
            <Button variant="ghost" className="btn-icon" onClick={reload} aria-label="Refresh planning" disabled={loading}>
              <RefreshCw size={15} />
            </Button>
          </>
        }
      />

      <ErrorState message={error} />
      {!data ? (
        !error && <TableSkeleton rows={4} cols={8} />
      ) : (
        <>
          <div className="planning-legend" aria-hidden="true">
            <span><i className="heat heat-1" /> Light</span>
            <span><i className="heat heat-3" /> Near capacity</span>
            <span><i className="heat heat-4" /> Over capacity</span>
          </div>
          <div className="table-container">
            <table className="table planning-table">
              <caption className="sr-only">Open tasks per member by due week, with overdue and blocked counts</caption>
              <thead>
                <tr>
                  <th scope="col">Member</th>
                  <th scope="col" className="num">Open</th>
                  <th scope="col" className="num">Overdue</th>
                  <th scope="col" className="num">Blocked</th>
                  {data.members[0]?.weeks.map((w, i) => (
                    <th scope="col" key={w.weekStart} className="num">{i === 0 ? 'This week' : `Wk ${formatDueDate(w.weekStart)}`}</th>
                  ))}
                  <th scope="col" className="num">Later</th>
                  <th scope="col" className="num">No date</th>
                  <th scope="col">Capacity</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((m) => (
                  <tr key={m.userId ?? 'unassigned'}>
                    <th scope="row">
                      {m.userId ? (
                        <Link to={`/tenants/${tenantId}/members/${m.userId}`} className="cell-link">
                          <Avatar name={m.name ?? '?'} size="xs" />
                          <span className="truncate">{m.name}</span>
                        </Link>
                      ) : (
                        <Link to={tasksFor('open')} className="cell-link text-muted">Unassigned</Link>
                      )}
                      {m.role && <RoleBadge role={m.role} />}
                      {m.warning && (
                        <div className="load-warning"><AlertTriangle size={11} aria-hidden="true" /> {m.warning}</div>
                      )}
                    </th>
                    <td className="num">{m.open}</td>
                    <td className={`num ${m.overdue ? 'text-danger' : ''}`}>{m.overdue}</td>
                    <td className={`num ${m.blocked ? 'text-warning' : ''}`}>{m.blocked}</td>
                    {m.weeks.map((w, i) => (
                      <td key={w.weekStart} className="num">
                        <span className={`heat heat-${level(w.due + (i === 0 ? m.overdue : 0), m.weeklyCapacity, max)}`} aria-label={`${w.due} due`}>
                          {w.due || ''}
                        </span>
                      </td>
                    ))}
                    <td className="num">{m.later}</td>
                    <td className="num">{m.noDueDate}</td>
                    <td>
                      {m.userId ? (
                        <CapacityEditor
                          key={`${m.userId}-${m.weeklyCapacity}`}
                          member={m}
                          canEdit={isAdmin || m.userId === user?.id}
                          onSave={(v) => saveCapacity(m, v)}
                        />
                      ) : (
                        <span className="text-muted">—</span>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <section className="panel mt-6" aria-labelledby="by-project">
            <div className="panel-head"><h2 id="by-project" className="section-heading">Open work by project</h2></div>
            <DataTable
              caption="Open tasks per member and project"
              columns={['Member', 'Project', 'Open tasks']}
              rows={rows.flatMap((m) => m.projects.map((p) => [m.name ?? 'Unassigned', p.projectName, p.open]))}
            />
          </section>

          <p className="analytics-note mt-4">
            <Info size={12} aria-hidden="true" />
            Counts show where work sits so it can be shared out; they are not a measure of anyone's productivity.
            The first week also includes overdue tasks when comparing with capacity.
          </p>
        </>
      )}
    </div>
  );
};

export default Planning;
