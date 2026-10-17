import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { Link } from 'react-router-dom';
import { Link2, X } from 'lucide-react';
import { dependencyApi, type Dependencies, type TaskLookup, type TaskRef } from '../../api/planning';
import { apiError } from '../../api/work';
import { useClickOutside } from '../../hooks/useClickOutside';
import { taskHref } from './taskLinks';
import { STATUS_LABELS } from './taskMeta';

interface TaskDependenciesProps {
  tenantId: string;
  taskId: string;
  /** Called with the new count of unfinished blockers after a change. */
  onChange?: (openBlockers: number) => void;
}

/**
 * "Blocked by" for one task: a clear summary, both directions, and a searchable picker to
 * add a blocker. The server rejects self-links, cycles and tasks from other workspaces and
 * explains why; that message is shown as-is.
 */
export const TaskDependencies = ({ tenantId, taskId, onChange }: TaskDependenciesProps) => {
  const [deps, setDeps] = useState<Dependencies | null>(null);
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<TaskLookup[]>([]);
  const [active, setActive] = useState(0);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const pickerRef = useRef<HTMLDivElement>(null);
  const listId = useId();
  useClickOutside(pickerRef, () => setOpen(false), open);

  useEffect(() => {
    const controller = new AbortController();
    dependencyApi
      .get(tenantId, taskId, controller.signal)
      .then(setDeps)
      .catch((err) => !controller.signal.aborted && setError(apiError(err, 'Could not load dependencies').message));
    return () => controller.abort();
  }, [tenantId, taskId]);

  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      dependencyApi
        .lookup(tenantId, query.trim(), controller.signal)
        .then((r) => {
          const linked = new Set([taskId, ...(deps?.blockedBy.map((d) => d.id) ?? [])]);
          setResults(r.filter((t) => !linked.has(t.id)));
          setActive(0);
        })
        .catch(() => undefined);
    }, 200);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [tenantId, taskId, query, open, deps]);

  const apply = (next: Dependencies) => {
    setDeps(next);
    setError('');
    onChange?.(next.openBlockers);
  };

  const add = async (t: TaskLookup) => {
    setBusy(true);
    try {
      apply(await dependencyApi.add(tenantId, taskId, t.id));
      setQuery('');
      setOpen(false);
    } catch (err) {
      setError(apiError(err, 'Could not add the dependency').message);
    } finally {
      setBusy(false);
    }
  };

  const remove = async (ref: TaskRef) => {
    setBusy(true);
    try {
      apply(await dependencyApi.remove(tenantId, taskId, ref.dependencyId));
    } catch (err) {
      setError(apiError(err, 'Could not remove the dependency').message);
    } finally {
      setBusy(false);
    }
  };

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setOpen(true);
      setActive((a) => Math.min(a + 1, results.length - 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((a) => Math.max(a - 1, 0));
    } else if (e.key === 'Enter' && open && results[active]) {
      e.preventDefault();
      void add(results[active]);
    } else if (e.key === 'Escape' && open) {
      e.stopPropagation();
      setOpen(false);
    }
  };

  const row = (r: TaskRef) => (
    <li key={r.dependencyId} className={r.status === 'DONE' ? 'is-done' : ''}>
      <span className={`status-dot is-${r.status.toLowerCase()}`} aria-hidden="true" />
      <Link to={taskHref(tenantId, r)} title={`${r.title} · ${r.projectName} · ${STATUS_LABELS[r.status]}`}>{r.title}</Link>
      <button type="button" className="icon-button" disabled={busy} onClick={() => remove(r)} aria-label={`Remove dependency on ${r.title}`}>
        <X size={13} />
      </button>
    </li>
  );

  return (
    <div className="dep-section" aria-label="Dependencies">
      <div className="form-label flex items-center gap-1"><Link2 size={13} aria-hidden="true" /> Dependencies</div>
      {deps && (
        deps.openBlockers > 0 ? (
          <p className="dep-summary is-blocked" role="status">
            Blocked: waiting on {deps.openBlockers} unfinished task{deps.openBlockers === 1 ? '' : 's'}. It can be marked done once they are.
          </p>
        ) : deps.blockedBy.length > 0 ? (
          <p className="dep-summary is-clear" role="status">Not blocked: everything it waits on is done.</p>
        ) : null
      )}
      {error && <p className="text-xs text-danger" role="alert">{error}</p>}
      {deps && deps.blockedBy.length > 0 && (
        <>
          <p className="dep-heading">Blocked by</p>
          <ul className="dep-list">{deps.blockedBy.map(row)}</ul>
        </>
      )}
      {deps && deps.blocks.length > 0 && (
        <>
          <p className="dep-heading">Blocks</p>
          <ul className="dep-list">{deps.blocks.map(row)}</ul>
        </>
      )}
      <div className="task-picker" ref={pickerRef}>
        <input
          type="text"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
          placeholder="Add a task this waits on…"
          aria-label="Find a task that blocks this one"
          role="combobox"
          aria-expanded={open && results.length > 0}
          aria-controls={listId}
          aria-activedescendant={open && results[active] ? `${listId}-${results[active].id}` : undefined}
          disabled={busy}
          style={{ height: '2rem', fontSize: 'var(--text-sm)' }}
        />
        {open && results.length > 0 && (
          <ul className="task-picker-results" role="listbox" id={listId} aria-label="Matching tasks">
            {results.map((t, i) => (
              <li
                key={t.id}
                id={`${listId}-${t.id}`}
                role="option"
                aria-selected={i === active}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => add(t)}
              >
                <span className="truncate">{t.title}</span>
                <span className="text-xs text-muted">{t.projectName} · {STATUS_LABELS[t.status]}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
};
