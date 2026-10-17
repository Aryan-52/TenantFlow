import { useRef, useState, type FormEvent } from 'react';
import { Bookmark, Check, Trash2 } from 'lucide-react';
import { useClickOutside } from '../../hooks/useClickOutside';

export interface TaskFilters {
  search: string;
  status: string;
  priority: string;
  assignee: string;
  due: string;
  sort: string;
}

interface SavedView {
  name: string;
  filters: TaskFilters;
}

const MAX_VIEWS = 12;

const read = (key: string): SavedView[] => {
  try {
    const raw = localStorage.getItem(key);
    const parsed = raw ? (JSON.parse(raw) as SavedView[]) : [];
    return Array.isArray(parsed) ? parsed.filter((v) => v && typeof v.name === 'string' && v.filters) : [];
  } catch {
    return [];
  }
};

const write = (key: string, views: SavedView[]) => {
  try {
    localStorage.setItem(key, JSON.stringify(views));
  } catch {
    // Storage unavailable: views last for this page only.
  }
};

interface SavedViewsProps {
  /** Scope, e.g. "tf.taskViews.<workspaceId>": views never leak between workspaces. */
  storageKey: string;
  current: TaskFilters;
  onApply: (filters: TaskFilters) => void;
}

/**
 * Named filter/sort combinations for the task list, saved in this browser per workspace
 * (they are personal shortcuts, not shared workspace data).
 */
export const SavedViews = ({ storageKey, current, onApply }: SavedViewsProps) => {
  const [open, setOpen] = useState(false);
  const [views, setViews] = useState<SavedView[]>(() => read(storageKey));
  const [name, setName] = useState('');
  const ref = useRef<HTMLDivElement>(null);
  useClickOutside(ref, () => setOpen(false), open);

  const save = (e: FormEvent) => {
    e.preventDefault();
    const trimmed = name.trim().slice(0, 40);
    if (!trimmed) return;
    const next = [{ name: trimmed, filters: current }, ...views.filter((v) => v.name !== trimmed)].slice(0, MAX_VIEWS);
    setViews(next);
    write(storageKey, next);
    setName('');
  };

  const remove = (viewName: string) => {
    const next = views.filter((v) => v.name !== viewName);
    setViews(next);
    write(storageKey, next);
  };

  const isActive = (v: SavedView) => JSON.stringify(v.filters) === JSON.stringify(current);

  return (
    <div className="saved-views" ref={ref}>
      <button
        type="button"
        className="btn btn-secondary saved-views-trigger"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <Bookmark size={15} aria-hidden="true" /> Views
      </button>
      {open && (
        <div className="saved-views-popover" role="dialog" aria-label="Saved views">
          {views.length === 0 ? (
            <p className="text-xs text-muted">Save the current filters and sort to reuse them later.</p>
          ) : (
            <ul className="saved-views-list">
              {views.map((v) => (
                <li key={v.name}>
                  <button
                    type="button"
                    className={`saved-view ${isActive(v) ? 'is-active' : ''}`.trim()}
                    onClick={() => {
                      onApply(v.filters);
                      setOpen(false);
                    }}
                  >
                    {isActive(v) ? <Check size={13} aria-hidden="true" /> : <Bookmark size={13} aria-hidden="true" />}
                    <span className="truncate">{v.name}</span>
                  </button>
                  <button type="button" className="icon-button" onClick={() => remove(v.name)} aria-label={`Delete view ${v.name}`}>
                    <Trash2 size={13} />
                  </button>
                </li>
              ))}
            </ul>
          )}
          <form className="saved-views-form" onSubmit={save}>
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Name this view" aria-label="View name" maxLength={40} />
            <button type="submit" className="btn btn-primary btn-sm" disabled={!name.trim()}>
              Save
            </button>
          </form>
        </div>
      )}
    </div>
  );
};
