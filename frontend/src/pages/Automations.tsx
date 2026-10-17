import { useState } from 'react';
import { ArrowRight, Info, RefreshCw, Workflow } from 'lucide-react';
import { automationApi, type AutomationRule, type AutomationRun, type AutomationType } from '../api/workspace';
import { timeAgo, type Page } from '../api/activity';
import { apiError } from '../api/work';
import { useTenant } from '../contexts/TenantContext';
import { useToast } from '../contexts/ToastContext';
import { useLoad } from '../hooks/useLoad';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { ErrorState } from '../components/ui/ErrorState';
import { Skeleton } from '../components/ui/Skeleton';
import { Badge } from '../components/ui/Badge';

const NAMES: Record<AutomationType, string> = {
  OVERDUE_NOTIFY_OWNER: 'Escalate overdue tasks',
  URGENT_NOTIFY_OWNER: 'Flag urgent work',
  UNBLOCKED_NOTIFY_ASSIGNEE: 'Announce unblocked tasks',
  MILESTONE_DONE_NOTIFY_ASSIGNEES: 'Milestone completion follow-up',
};

const OUTCOME_COLOR = { SUCCESS: 'green', SKIPPED: 'gray', FAILED: 'red' } as const;

/**
 * Fixed rules that can be switched on per workspace. Their actions only send in-app
 * notifications, so one rule can never trigger another, and each trigger runs once.
 */
const Automations = () => {
  const { currentTenant } = useTenant();
  const toast = useToast();
  const tenantId = currentTenant?.id ?? '';
  const isAdmin = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';
  const rules = useLoad<AutomationRule[]>((s) => automationApi.rules(tenantId, s), [tenantId], 'Could not load automations.');
  const [page, setPage] = useState(0);
  const runs = useLoad<Page<AutomationRun>>((s) => automationApi.runs(tenantId, page, 20, s), [tenantId, page], 'Could not load the history.');
  const [saving, setSaving] = useState<AutomationType | null>(null);

  const toggle = async (rule: AutomationRule) => {
    setSaving(rule.type);
    try {
      rules.setData(await automationApi.toggle(tenantId, rule.type, !rule.enabled));
      toast.success(`${NAMES[rule.type]} ${rule.enabled ? 'turned off' : 'turned on'}`);
    } catch (err) {
      toast.error(apiError(err, 'Could not change the rule').message);
    } finally {
      setSaving(null);
    }
  };

  if (!currentTenant) return null;

  return (
    <div>
      <PageHeader
        title="Automations"
        subtitle={isAdmin ? 'Switch on the rules this workspace needs. Each is off until an admin turns it on.' : 'Rules running in this workspace. Admins can switch them on or off.'}
        actions={
          <Button variant="ghost" className="btn-icon" aria-label="Refresh" onClick={() => { rules.reload(); runs.reload(); }}>
            <RefreshCw size={15} />
          </Button>
        }
      />

      <ErrorState message={rules.error} />
      {!rules.data ? (
        !rules.error && <Skeleton style={{ height: '12rem' }} />
      ) : (
        <ul className="rule-list">
          {rules.data.map((r) => (
            <li key={r.type} className={`rule-card ${r.enabled ? 'is-on' : ''}`}>
              <div className="rule-icon" aria-hidden="true"><Workflow size={18} /></div>
              <div className="rule-body">
                <h2 className="rule-name">{NAMES[r.type]}</h2>
                <p className="rule-flow">
                  <span><b>When</b> {r.trigger.charAt(0).toLowerCase() + r.trigger.slice(1)}</span>
                  <ArrowRight size={13} aria-hidden="true" />
                  <span><b>Then</b> {r.action.charAt(0).toLowerCase() + r.action.slice(1)}</span>
                </p>
                <p className="text-xs text-muted">
                  {r.runs === 0 ? 'Has not run yet' : `${r.runs} run${r.runs === 1 ? '' : 's'}${r.lastRunAt ? `, last ${timeAgo(r.lastRunAt)}` : ''}`}
                  {r.failures > 0 && <span className="text-danger"> · {r.failures} failed</span>}
                </p>
              </div>
              <label className="switch">
                <input
                  type="checkbox"
                  role="switch"
                  checked={r.enabled}
                  disabled={!isAdmin || saving === r.type}
                  onChange={() => toggle(r)}
                  aria-label={`${NAMES[r.type]}: ${r.enabled ? 'on' : 'off'}`}
                />
                <span className="switch-track" aria-hidden="true" />
                <span className="switch-label">{r.enabled ? 'On' : 'Off'}</span>
              </label>
            </li>
          ))}
        </ul>
      )}

      <section className="panel mt-6" aria-labelledby="runs-title">
        <div className="panel-head">
          <h2 id="runs-title" className="section-heading">Execution history</h2>
          <span className="text-xs text-muted">Every trigger handled, newest first</span>
        </div>
        <ErrorState message={runs.error} />
        {!runs.data ? (
          !runs.error && <Skeleton style={{ height: '6rem' }} />
        ) : runs.data.items.length === 0 ? (
          <p className="dash-empty">No runs yet.</p>
        ) : (
          <>
            <ul className="run-list">
              {runs.data.items.map((r) => (
                <li key={r.id}>
                  <Badge color={OUTCOME_COLOR[r.outcome]}>{r.outcome === 'SUCCESS' ? 'Done' : r.outcome === 'SKIPPED' ? 'Skipped' : 'Failed'}</Badge>
                  <div className="min-w-0">
                    <div className="text-sm">{r.message}</div>
                    <div className="text-xs text-muted">{NAMES[r.type]} · <span title={new Date(r.createdAt).toLocaleString()}>{timeAgo(r.createdAt)}</span></div>
                  </div>
                </li>
              ))}
            </ul>
            {runs.data.totalPages > 1 && (
              <nav className="pagination" aria-label="History pages">
                <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>Newer</Button>
                <span className="text-sm text-muted">Page {page + 1} of {runs.data.totalPages}</span>
                <Button variant="secondary" size="sm" disabled={!runs.data.hasNext} onClick={() => setPage((p) => p + 1)}>Older</Button>
              </nav>
            )}
          </>
        )}
      </section>

      <p className="analytics-note mt-4">
        <Info size={12} aria-hidden="true" />
        Rules only notify people who are members of this workspace and never the person who made the change.
        Skipped runs explain why nothing was sent.
      </p>
    </div>
  );
};

export default Automations;
