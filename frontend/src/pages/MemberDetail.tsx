import { Link, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { planningApi, type MemberOverview } from '../api/planning';
import { timeAgo } from '../api/activity';
import type { Task } from '../api/work';
import { useTenant } from '../contexts/TenantContext';
import { useLoad } from '../hooks/useLoad';
import { useWorkspaceData } from '../hooks/useWorkspaceData';
import { Avatar } from '../components/ui/Avatar';
import { RoleBadge } from '../components/ui/Badge';
import { ErrorState } from '../components/ui/ErrorState';
import { Skeleton } from '../components/ui/Skeleton';
import { TaskRow } from '../components/tasks/TaskRow';
import { taskHref } from '../components/tasks/taskLinks';
import { todayKey } from '../components/tasks/taskMeta';

/** One member's role, current workload and recent work in this workspace. */
const MemberDetail = () => {
  const { memberId = '' } = useParams();
  const { currentTenant } = useTenant();
  const tenantId = currentTenant?.id ?? '';
  const { data, setData, error } = useLoad<MemberOverview>((s) => planningApi.member(tenantId, memberId, s), [tenantId, memberId], 'Could not load this member.');
  const { members } = useWorkspaceData(tenantId);

  const replace = (task: Task) =>
    setData((d) => {
      if (!d) return d;
      const stays = task.status !== 'DONE' && task.assigneeId === d.userId;
      return {
        ...d,
        open: stays ? d.open.map((t) => (t.id === task.id ? task : t)) : d.open.filter((t) => t.id !== task.id),
        completedRecently: task.status === 'DONE' ? [task, ...d.completedRecently] : d.completedRecently,
      };
    });

  if (!currentTenant) return null;
  const today = todayKey();
  const overdue = data?.open.filter((t) => t.dueDate && t.dueDate < today).length ?? 0;
  const blocked = data?.open.filter((t) => (t.openBlockers ?? 0) > 0).length ?? 0;

  return (
    <div>
      <Link to={`/tenants/${tenantId}/members`} className="back-link"><ArrowLeft size={14} aria-hidden="true" /> Team</Link>
      <ErrorState message={error} />
      {!data ? (
        !error && <Skeleton style={{ height: '12rem' }} />
      ) : (
        <>
          <header className="member-head">
            <Avatar name={data.name} size="lg" />
            <div className="min-w-0">
              <h1 className="page-title">{data.name}</h1>
              <p className="page-subtitle">
                {data.email} · <RoleBadge role={data.role} /> · joined {timeAgo(data.joinedAt)}
              </p>
            </div>
          </header>

          <dl className="stat-row" aria-label="Current workload">
            <div><dt>Open tasks</dt><dd>{data.open.length}</dd></div>
            <div><dt>Overdue</dt><dd className={overdue ? 'text-danger' : ''}>{overdue}</dd></div>
            <div><dt>Blocked</dt><dd>{blocked}</dd></div>
            <div><dt>Capacity</dt><dd>{data.weeklyCapacity === null ? '—' : `${data.weeklyCapacity} / week`}</dd></div>
            <div><dt>Completed (30 days)</dt><dd>{data.completedRecently.length}</dd></div>
          </dl>

          <div className="two-col">
            <section className="panel" aria-labelledby="m-open">
              <div className="panel-head"><h2 id="m-open" className="section-heading">Assigned work</h2></div>
              {data.open.length === 0 ? (
                <p className="dash-empty">No open tasks.</p>
              ) : (
                <ul className="work-list">
                  {data.open.map((t) => (
                    <TaskRow key={t.id} tenantId={tenantId} task={t} onChange={replace} members={members} />
                  ))}
                </ul>
              )}
            </section>
            <div className="flex flex-col gap-4">
              <section className="panel" aria-labelledby="m-projects">
                <div className="panel-head"><h2 id="m-projects" className="section-heading">Projects</h2></div>
                {data.projects.length === 0 ? (
                  <p className="dash-empty">Not working on any project right now.</p>
                ) : (
                  <ul className="plain-list">
                    {data.projects.map((p) => (
                      <li key={p.projectId}>
                        <Link to={`/tenants/${tenantId}/projects/${p.projectId}`}>{p.projectName}</Link>
                        <span className="text-muted text-xs">{p.open} open</span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
              <section className="panel" aria-labelledby="m-done">
                <div className="panel-head"><h2 id="m-done" className="section-heading">Completed recently</h2></div>
                {data.completedRecently.length === 0 ? (
                  <p className="dash-empty">Nothing in the last 30 days.</p>
                ) : (
                  <ul className="plain-list">
                    {data.completedRecently.map((t) => (
                      <li key={t.id}>
                        <Link to={taskHref(tenantId, t)}>{t.title}</Link>
                        <span className="text-muted text-xs">{t.completedAt ? timeAgo(t.completedAt) : ''}</span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
            </div>
          </div>
          <p className="text-xs text-muted mt-4">Only work in this workspace is shown. Counts describe current assignments, not performance.</p>
        </>
      )}
    </div>
  );
};

export default MemberDetail;
