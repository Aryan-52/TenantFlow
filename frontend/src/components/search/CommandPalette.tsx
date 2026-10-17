import { useEffect, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { BarChart3, Bell, CalendarDays, Briefcase, FileStack, FileBarChart, SquareChartGantt, Sun, Workflow, SlidersHorizontal, CheckSquare, Clock, FileText, FolderKanban, FolderPlus, LayoutDashboard, Loader2, Plus, Search, Settings, User, UserCircle, Users } from 'lucide-react';
import { searchApi, SEARCH_MIN_LENGTH, type SearchResult } from '../../api/search';
import { useTenant } from '../../contexts/TenantContext';

interface CommandPaletteProps {
  onClose: () => void;
  /** Opens the "New task" dialog (hosted by the layout); the action is hidden without it. */
  onCreateTask?: () => void;
}

type Group = 'Recent' | 'Actions' | 'Go to' | 'Projects' | 'Tasks' | 'People' | 'Files';
type Kind = 'project' | 'task' | 'member' | 'file';

interface Option {
  id: string;
  group: Group;
  label: string;
  hint?: string;
  icon: ReactNode;
  /** Where the option navigates; actions use run instead. */
  path?: string;
  run?: () => void;
  /** Search hits are remembered as recent destinations. */
  kind?: Kind;
}

const DEBOUNCE_MS = 250;
const GROUPS: Group[] = ['Recent', 'Actions', 'Go to', 'Projects', 'Tasks', 'People', 'Files'];

// ---- Recent destinations: the last few search hits opened, kept in this browser ----------

const RECENT_KEY = 'tf.paletteRecent';
const RECENT_MAX = 5;

interface Recent {
  id: string;
  label: string;
  hint?: string;
  path: string;
  kind: Kind;
}

const KIND_ICON: Record<Kind, ReactNode> = {
  project: <FolderKanban size={15} />,
  task: <CheckSquare size={15} />,
  member: <UserCircle size={15} />,
  file: <FileText size={15} />,
};

const readRecent = (): Recent[] => {
  try {
    const parsed = JSON.parse(localStorage.getItem(RECENT_KEY) ?? '[]');
    return Array.isArray(parsed) ? parsed.filter((r) => r && typeof r.path === 'string' && r.kind in KIND_ICON) : [];
  } catch {
    return [];
  }
};

const remember = (o: Option) => {
  if (!o.kind || !o.path) return;
  const entry: Recent = { id: o.id, label: o.label, hint: o.hint, path: o.path, kind: o.kind };
  try {
    const next = [entry, ...readRecent().filter((r) => r.id !== o.id)].slice(0, RECENT_MAX);
    localStorage.setItem(RECENT_KEY, JSON.stringify(next));
  } catch {
    // Recents are a convenience; ignore unavailable storage.
  }
};

/**
 * Keyboard-first command palette: navigation commands plus global search across every
 * workspace the user belongs to. Implements the ARIA combobox + listbox pattern. Render it
 * only while open, so every opening starts with fresh state.
 */
export const CommandPalette = ({ onClose, onCreateTask }: CommandPaletteProps) => {
  const navigate = useNavigate();
  const { currentTenant } = useTenant();
  const [query, setQuery] = useState('');
  const [result, setResult] = useState<SearchResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [active, setActive] = useState(0);
  const [recent] = useState(readRecent);
  const inputRef = useRef<HTMLInputElement>(null);

  // Return focus to whatever was focused before the palette opened.
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    inputRef.current?.focus();
    return () => previous?.focus?.();
  }, []);

  // Debounced search; in-flight requests are cancelled when the query changes.
  const trimmed = query.trim();
  const searching = trimmed.length >= SEARCH_MIN_LENGTH;
  useEffect(() => {
    if (!searching) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setLoading(true);
      searchApi
        .search(trimmed, controller.signal)
        .then((r) => {
          setResult(r);
          setError('');
        })
        .catch((err) => {
          if (controller.signal.aborted) return;
          setError(err?.response?.status === 429 ? 'Searching too quickly. Wait a moment and try again.' : 'Search failed. Try again.');
        })
        .finally(() => {
          if (!controller.signal.aborted) setLoading(false);
        });
    }, DEBOUNCE_MS);
    return () => {
      controller.abort();
      window.clearTimeout(timer);
    };
  }, [trimmed, searching]);
  const visibleResult = searching ? result : null;
  const isLoading = searching && loading;

  const options = useMemo<Option[]>(() => {
    const t = currentTenant?.id;
    const isAdmin = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';
    const actions: Option[] = t
      ? [
          ...(onCreateTask ? [{ id: 'act-task', group: 'Actions' as const, label: 'New task', icon: <Plus size={15} />, run: onCreateTask }] : []),
          ...(isAdmin
            ? [{ id: 'act-project', group: 'Actions' as const, label: 'New project', icon: <FolderPlus size={15} />, path: `/tenants/${t}/projects?new=1` }]
            : []),
        ]
      : [];
    const commands: Option[] = [
      ...actions,
      ...(t
        ? [
            { id: 'go-dashboard', group: 'Go to' as const, label: 'Overview', hint: 'Dashboard', icon: <LayoutDashboard size={15} />, path: `/tenants/${t}/dashboard` },
            { id: 'go-my-work', group: 'Go to' as const, label: 'My Work', icon: <Sun size={15} />, path: `/tenants/${t}/my-work` },
            { id: 'go-calendar', group: 'Go to' as const, label: 'Calendar', icon: <CalendarDays size={15} />, path: `/tenants/${t}/calendar` },
            { id: 'go-portfolio', group: 'Go to' as const, label: 'Portfolio', icon: <Briefcase size={15} />, path: `/tenants/${t}/portfolio` },
            { id: 'go-planning', group: 'Go to' as const, label: 'Planning', hint: 'Team workload', icon: <SquareChartGantt size={15} />, path: `/tenants/${t}/planning` },
            { id: 'go-documents', group: 'Go to' as const, label: 'Documents', icon: <FileStack size={15} />, path: `/tenants/${t}/documents` },
            { id: 'go-reports', group: 'Go to' as const, label: 'Reports', icon: <FileBarChart size={15} />, path: `/tenants/${t}/reports` },
            { id: 'go-automations', group: 'Go to' as const, label: 'Automations', icon: <Workflow size={15} />, path: `/tenants/${t}/automations` },
            { id: 'go-projects', group: 'Go to' as const, label: 'Projects', icon: <FolderKanban size={15} />, path: `/tenants/${t}/projects` },
            { id: 'go-tasks', group: 'Go to' as const, label: 'All tasks', icon: <CheckSquare size={15} />, path: `/tenants/${t}/tasks` },
            { id: 'go-members', group: 'Go to' as const, label: 'Team', hint: 'Members', icon: <Users size={15} />, path: `/tenants/${t}/members` },
            { id: 'go-analytics', group: 'Go to' as const, label: 'Analytics', icon: <BarChart3 size={15} />, path: `/tenants/${t}/analytics` },
          ]
        : []),
      ...(t && isAdmin
        ? [{ id: 'go-settings', group: 'Go to' as const, label: 'Workspace settings', icon: <Settings size={15} />, path: `/tenants/${t}/settings` }]
        : []),
      { id: 'go-notifications', group: 'Go to', label: 'Inbox', hint: 'Notifications', icon: <Bell size={15} />, path: '/notifications' },
      { id: 'go-preferences', group: 'Go to', label: 'Preferences', icon: <SlidersHorizontal size={15} />, path: '/preferences' },
      { id: 'go-profile', group: 'Go to', label: 'Your profile', icon: <User size={15} />, path: '/profile' },
    ];
    const q = trimmed.toLowerCase();
    const filteredCommands = q ? commands.filter((c) => `${c.label} ${c.hint ?? ''}`.toLowerCase().includes(q)) : commands;

    const projects: Option[] = (visibleResult?.projects?.items ?? []).map((p) => ({
      id: `project-${p.id}`,
      group: 'Projects',
      label: p.name,
      hint: p.tenantName,
      icon: KIND_ICON.project,
      path: `/tenants/${p.tenantId}/projects/${p.id}`,
      kind: 'project',
    }));
    const tasks: Option[] = (visibleResult?.tasks?.items ?? []).map((task) => ({
      id: `task-${task.id}`,
      group: 'Tasks',
      label: task.title,
      hint: `${task.projectName} · ${task.tenantName}`,
      icon: KIND_ICON.task,
      path: `/tenants/${task.tenantId}/projects/${task.projectId}/tasks?task=${task.id}`,
      kind: 'task',
    }));
    const people: Option[] = (visibleResult?.members?.items ?? []).map((m) => ({
      id: `member-${m.tenantId}-${m.userId}`,
      group: 'People',
      label: m.name,
      hint: `${m.email} · ${m.tenantName}`,
      icon: KIND_ICON.member,
      path: `/tenants/${m.tenantId}/members`,
      kind: 'member',
    }));
    const files: Option[] = (visibleResult?.files?.items ?? []).map((f) => ({
      id: `file-${f.id}`,
      group: 'Files',
      label: f.filename,
      hint: `${f.projectName ?? 'Project'} · ${f.tenantName}`,
      icon: KIND_ICON.file,
      path: `/tenants/${f.tenantId}/projects/${f.projectId}/files`,
      kind: 'file',
    }));
    const recents: Option[] = q ? [] : recent.map((r) => ({ ...r, id: `recent-${r.id}`, group: 'Recent', icon: <Clock size={15} /> }));
    return [...recents, ...filteredCommands, ...projects, ...tasks, ...people, ...files];
  }, [currentTenant, visibleResult, trimmed, recent, onCreateTask]);

  const activeIndex = options.length === 0 ? 0 : Math.min(active, options.length - 1);

  const choose = (option: Option | undefined) => {
    if (!option) return;
    remember(option);
    onClose();
    if (option.run) option.run();
    else if (option.path) navigate(option.path);
  };

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive(options.length ? (activeIndex + 1) % options.length : 0);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive(options.length ? (activeIndex - 1 + options.length) % options.length : 0);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      choose(options[activeIndex]);
    } else if (e.key === 'Escape') {
      e.preventDefault();
      onClose();
    }
  };

  const noResults = searching && !isLoading && !error && visibleResult
    && (visibleResult.projects?.items.length ?? 0) + (visibleResult.tasks?.items.length ?? 0)
      + (visibleResult.members?.items.length ?? 0) + (visibleResult.files?.items.length ?? 0) === 0;

  return (
    <div className="palette-backdrop" onMouseDown={onClose}>
      <div className="palette" role="dialog" aria-modal="true" aria-label="Search and commands" onMouseDown={(e) => e.stopPropagation()}>
        <div className="palette-input-row">
          {isLoading ? <Loader2 size={16} className="animate-spin text-muted" /> : <Search size={16} className="text-muted" />}
          <input
            ref={inputRef}
            className="palette-input"
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setActive(0);
            }}
            onKeyDown={onKeyDown}
            placeholder="Search projects, tasks, people and files, or jump to…"
            role="combobox"
            aria-expanded="true"
            aria-controls="palette-listbox"
            aria-activedescendant={options[activeIndex] ? `palette-${options[activeIndex].id}` : undefined}
            aria-autocomplete="list"
            aria-label="Search projects, tasks, people and files"
          />
          <kbd className="palette-kbd">Esc</kbd>
        </div>

        <ul id="palette-listbox" role="listbox" className="palette-list" aria-label="Results">
          {GROUPS.map((group) => {
            const items = options.filter((o) => o.group === group);
            if (items.length === 0) return null;
            return (
              <li key={group} role="presentation">
                <div className="palette-group" role="presentation">{group}</div>
                <ul role="group" aria-label={group}>
                  {items.map((o) => {
                    const index = options.indexOf(o);
                    return (
                      <li
                        key={o.id}
                        id={`palette-${o.id}`}
                        role="option"
                        aria-selected={index === activeIndex}
                        className={`palette-option ${index === activeIndex ? 'is-active' : ''}`.trim()}
                        onMouseMove={() => setActive(index)}
                        onClick={() => choose(o)}
                      >
                        <span className="text-muted">{o.icon}</span>
                        <span className="palette-label">{o.label}</span>
                        {o.hint && <span className="palette-hint">{o.hint}</span>}
                      </li>
                    );
                  })}
                </ul>
              </li>
            );
          })}
        </ul>

        <div className="palette-status" aria-live="polite">
          {error
            ? <span className="text-danger">{error}</span>
            : noResults
              ? `Nothing matches "${trimmed}".`
              : trimmed.length > 0 && trimmed.length < SEARCH_MIN_LENGTH
                ? `Type at least ${SEARCH_MIN_LENGTH} characters to search.`
                : <span>↑↓ to move · Enter to open · <kbd>g</kbd> then <kbd>d</kbd>/<kbd>p</kbd>/<kbd>t</kbd>/<kbd>n</kbd> to jump</span>}
        </div>
      </div>
    </div>
  );
};
