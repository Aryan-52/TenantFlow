import { useMemo, useState, type ReactNode } from 'react';
import { CalendarX2, SlidersHorizontal, SearchX, Trash2, X } from 'lucide-react';
import { SavedViews, type TaskFilters } from './SavedViews';
import { ConfirmDialog } from '../ui/ConfirmDialog';
import { Button } from '../ui/Button';
import { Avatar } from '../ui/Avatar';
import { TasksIllustration } from '../illustrations/Illustrations';
import type { Task, TaskPriority, TaskStatus } from '../../api/work';
import type { Member } from '../../pages/Members';
import { Select } from '../ui/Select';
import { SearchInput } from '../ui/SearchInput';
import { PriorityBadge } from '../ui/Badge';
import { Table } from '../ui/Table';
import { EmptyState } from '../ui/EmptyState';
import { TableSkeleton } from '../ui/Skeleton';
import { DueDateLabel } from './DueDateLabel';
import { PRIORITY_OPTIONS, PRIORITY_RANK, STATUS_OPTIONS, compareByDueThenPriority, dueState, isDueWithinDays } from './taskMeta';

type SortOption = 'due' | 'priority' | 'updated' | 'title';
type DueFilter = '' | 'overdue' | 'today' | 'week' | 'none';

const DUE_OPTIONS: { value: DueFilter; label: string }[] = [
  { value: '', label: 'Any due date' },
  { value: 'overdue', label: 'Overdue' },
  { value: 'today', label: 'Due today' },
  { value: 'week', label: 'Due in next 7 days' },
  { value: 'none', label: 'No due date' },
];

const matchesDue = (task: Task, filter: DueFilter) => {
  switch (filter) {
    case 'overdue':
      return dueState(task) === 'overdue';
    case 'today':
      return dueState(task) === 'today';
    case 'week':
      // Open tasks due from today through the next 7 days (overdue ones have their own option).
      return isDueWithinDays(task, 7) && dueState(task) !== 'overdue';
    case 'none':
      return !task.dueDate;
    default:
      return true;
  }
};

const SORT_OPTIONS: { value: SortOption; label: string }[] = [
  { value: 'due', label: 'Sort: due date' },
  { value: 'priority', label: 'Sort: priority' },
  { value: 'updated', label: 'Sort: recently updated' },
  { value: 'title', label: 'Sort: title' },
];

interface TaskTableProps {
  tasks: Task[];
  members: Member[];
  onOpen: (task: Task) => void;
  onStatusChange: (task: Task, status: TaskStatus) => void;
  /** Shown in the empty state when no filters are active. */
  emptyAction?: ReactNode;
  /** Optional project selector shown at the start of the toolbar. */
  projectSelector?: ReactNode;
  /** Optional primary action (e.g. "Create task") shown at the end of the toolbar. */
  primaryAction?: ReactNode;
  /** Tasks are (re)loading: keep the toolbar in place and show a skeleton for the list. */
  loading?: boolean;
  /** Tasks come from several projects: show each task's project. */
  showProject?: boolean;
  /** Adds "Assigned to me" to the assignee filter. */
  currentUserId?: string;
  /** Enables saved views, stored per this key (e.g. one per workspace). */
  viewsKey?: string;
  /** Enables row selection and the bulk-action bar. remove is only passed to admins. */
  bulk?: {
    busy: boolean;
    setStatus: (tasks: Task[], status: TaskStatus) => Promise<void> | void;
    assign: (tasks: Task[], assigneeId: string | null) => Promise<void> | void;
    setPriority?: (tasks: Task[], priority: TaskPriority) => Promise<void> | void;
    setDueDate?: (tasks: Task[], dueDate: string | null) => Promise<void> | void;
    remove?: (tasks: Task[]) => Promise<void> | void;
  };
}

/** Filterable, sortable task list. Clicking a row opens the task's detail dialog. */
export const TaskTable = ({
  tasks,
  members,
  onOpen,
  onStatusChange,
  emptyAction,
  projectSelector,
  primaryAction,
  loading = false,
  showProject = false,
  bulk,
  currentUserId,
  viewsKey,
}: TaskTableProps) => {
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [bulkDue, setBulkDue] = useState('');
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [confirmBulkDelete, setConfirmBulkDelete] = useState(false);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [priorityFilter, setPriorityFilter] = useState('');
  const [assigneeFilter, setAssigneeFilter] = useState('');
  const [dueFilter, setDueFilter] = useState<DueFilter>('');
  const [sort, setSort] = useState<SortOption>('due');

  const visibleTasks = useMemo(() => {
    const q = search.trim().toLowerCase();
    const filtered = tasks.filter((t) => {
      if (q && !t.title.toLowerCase().includes(q) && !(t.description ?? '').toLowerCase().includes(q)) return false;
      if (statusFilter && t.status !== statusFilter) return false;
      if (priorityFilter && t.priority !== priorityFilter) return false;
      if (!matchesDue(t, dueFilter)) return false;
      if (assigneeFilter === 'unassigned') return !t.assigneeId;
      if (assigneeFilter === 'me') return !!currentUserId && t.assigneeId === currentUserId;
      if (assigneeFilter && t.assigneeId !== assigneeFilter) return false;
      return true;
    });
    const sorted = [...filtered];
    switch (sort) {
      case 'priority':
        sorted.sort((a, b) => PRIORITY_RANK[b.priority] - PRIORITY_RANK[a.priority] || compareByDueThenPriority(a, b));
        break;
      case 'updated':
        sorted.sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
        break;
      case 'title':
        sorted.sort((a, b) => a.title.localeCompare(b.title));
        break;
      case 'due':
      default:
        sorted.sort(compareByDueThenPriority);
    }
    return sorted;
  }, [tasks, search, statusFilter, priorityFilter, assigneeFilter, dueFilter, sort, currentUserId]);

  const hasActiveFilters = !!(search || statusFilter || priorityFilter || assigneeFilter || dueFilter);
  const activeFilterCount = [statusFilter, priorityFilter, assigneeFilter, dueFilter].filter(Boolean).length;
  const currentFilters: TaskFilters = { search, status: statusFilter, priority: priorityFilter, assignee: assigneeFilter, due: dueFilter, sort };
  const applyFilters = (f: TaskFilters) => {
    setSearch(f.search ?? '');
    setStatusFilter(f.status ?? '');
    setPriorityFilter(f.priority ?? '');
    setAssigneeFilter(f.assignee === 'me' && !currentUserId ? '' : f.assignee ?? '');
    setDueFilter((f.due ?? '') as DueFilter);
    setSort((f.sort || 'due') as SortOption);
  };

  // Only rows that are visible (and still exist) count as selected.
  const selectedTasks = visibleTasks.filter((t) => selected.has(t.id));
  const allSelected = visibleTasks.length > 0 && selectedTasks.length === visibleTasks.length;
  const toggle = (id: string) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  const toggleAll = () => setSelected(allSelected ? new Set() : new Set(visibleTasks.map((t) => t.id)));
  const clearSelection = () => setSelected(new Set());
  const applyBulk = async (fn: () => Promise<void> | void) => {
    await fn();
    clearSelection();
  };

  return (
    <div>
      {/* One grid toolbar: project / search / filters / primary action share edges and
          heights at every width (see .task-toolbar in index.css for the breakpoints). */}
      <div className={`task-toolbar ${projectSelector ? 'has-project' : ''} ${filtersOpen ? 'filters-open' : ''}`.replace(/\s+/g, ' ').trim()}>
        {projectSelector && <div className="task-toolbar-project">{projectSelector}</div>}
        <div className="task-toolbar-search">
          <SearchInput value={search} onChange={setSearch} placeholder="Search tasks..." className="flex-1" />
          <button
            type="button"
            className="btn btn-secondary filters-toggle"
            aria-expanded={filtersOpen}
            aria-controls="task-filters"
            onClick={() => setFiltersOpen((o) => !o)}
          >
            <SlidersHorizontal size={15} aria-hidden="true" /> Filters{activeFilterCount > 0 ? ` · ${activeFilterCount}` : ''}
          </button>
          {viewsKey && <SavedViews storageKey={viewsKey} current={currentFilters} onApply={applyFilters} />}
        </div>
        <div className="task-toolbar-filters" id="task-filters">
        <Select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          options={[{ value: '', label: 'All statuses' }, ...STATUS_OPTIONS]}
          aria-label="Filter by status"
        />
        <Select
          value={priorityFilter}
          onChange={(e) => setPriorityFilter(e.target.value)}
          options={[{ value: '', label: 'All priorities' }, ...PRIORITY_OPTIONS]}
          aria-label="Filter by priority"
        />
        <Select
          value={assigneeFilter}
          onChange={(e) => setAssigneeFilter(e.target.value)}
          options={[
            { value: '', label: 'All assignees' },
            ...(currentUserId ? [{ value: 'me', label: 'Assigned to me' }] : []),
            { value: 'unassigned', label: 'Unassigned' },
            ...members.map((m) => ({ value: m.userId, label: m.name })),
          ]}
          aria-label="Filter by assignee"
        />
        <Select
          value={dueFilter}
          onChange={(e) => setDueFilter(e.target.value as DueFilter)}
          options={DUE_OPTIONS}
          aria-label="Filter by due date"
        />
        <Select
          value={sort}
          onChange={(e) => setSort(e.target.value as SortOption)}
          options={SORT_OPTIONS}
          aria-label="Sort tasks"
        />
        </div>
        {primaryAction && <div className="task-toolbar-action">{primaryAction}</div>}
      </div>

      {loading ? (
        <TableSkeleton rows={5} cols={5} />
      ) : visibleTasks.length === 0 ? (
        hasActiveFilters ? (
          <EmptyState
            icon={<SearchX size={22} />}
            title="No tasks found"
            description="We couldn't find any tasks matching your filters."
          />
        ) : (
          <EmptyState
            illustration={<TasksIllustration />}
            title={showProject ? 'All clear' : 'No tasks yet'}
            description={showProject ? 'No open tasks match this view.' : "This project doesn't have any tasks yet. Add one to get started."}
            action={emptyAction}
          />
        )
      ) : (
        <>
        {bulk && selectedTasks.length > 0 && (
          <div className="bulk-bar" role="region" aria-label="Bulk actions">
            <span className="bulk-count">
              {selectedTasks.length} selected
            </span>
            <select
              aria-label="Set status for selected tasks"
              value=""
              disabled={bulk.busy}
              onChange={(e) => e.target.value && applyBulk(() => bulk.setStatus(selectedTasks, e.target.value as TaskStatus))}
            >
              <option value="">Set status…</option>
              {STATUS_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
            <select
              aria-label="Assign selected tasks"
              value=""
              disabled={bulk.busy}
              onChange={(e) => {
                const v = e.target.value;
                if (!v) return;
                applyBulk(() => bulk.assign(selectedTasks, v === 'unassigned' ? null : v));
              }}
            >
              <option value="">Assign to…</option>
              <option value="unassigned">Unassigned</option>
              {members.map((m) => (
                <option key={m.userId} value={m.userId}>{m.name}</option>
              ))}
            </select>
            {bulk.setPriority && (
              <select
                aria-label="Set priority for selected tasks"
                value=""
                disabled={bulk.busy}
                onChange={(e) => e.target.value && applyBulk(() => bulk.setPriority!(selectedTasks, e.target.value as TaskPriority))}
              >
                <option value="">Set priority…</option>
                {PRIORITY_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>{o.label}</option>
                ))}
              </select>
            )}
            {bulk.setDueDate && (
              <span className="bulk-due">
                <input
                  type="date"
                  value={bulkDue}
                  onChange={(e) => setBulkDue(e.target.value)}
                  aria-label="Due date for selected tasks"
                  disabled={bulk.busy}
                />
                <Button variant="secondary" size="sm" disabled={!bulkDue || bulk.busy} onClick={() => applyBulk(() => bulk.setDueDate!(selectedTasks, bulkDue)).then(() => setBulkDue(''))}>
                  Set due
                </Button>
                <button type="button" className="icon-button" disabled={bulk.busy} onClick={() => applyBulk(() => bulk.setDueDate!(selectedTasks, null))} aria-label="Clear due date of selected tasks" data-tooltip="Clear due date">
                  <CalendarX2 size={15} />
                </button>
              </span>
            )}
            {bulk.remove && (
              <Button variant="ghost" size="sm" className="text-danger" onClick={() => setConfirmBulkDelete(true)} disabled={bulk.busy}>
                <Trash2 size={14} /> Delete
              </Button>
            )}
            <button type="button" className="icon-button ml-auto" onClick={clearSelection} aria-label="Clear selection" data-tooltip="Clear selection">
              <X size={15} />
            </button>
          </div>
        )}
        <Table>
          <thead>
            <tr>
              {bulk && (
                <th className="select-cell">
                  <input type="checkbox" checked={allSelected} onChange={toggleAll} aria-label="Select all tasks" />
                </th>
              )}
              <th>Task</th>
              <th>Status</th>
              <th>Priority</th>
              <th>Assignee</th>
              <th>Due</th>
            </tr>
          </thead>
          <tbody>
            {visibleTasks.map((t) => (
              <tr key={t.id} className={`table-row-clickable ${selected.has(t.id) ? 'is-selected' : ''}`.trim()} onClick={() => onOpen(t)}>
                {bulk && (
                  <td className="select-cell" onClick={(e) => e.stopPropagation()}>
                    <input type="checkbox" checked={selected.has(t.id)} onChange={() => toggle(t.id)} aria-label={`Select ${t.title}`} />
                  </td>
                )}
                <td style={{ maxWidth: '420px' }}>
                  <div className="task-title-cell">
                    <button
                      type="button"
                      className="link-button font-medium text-main"
                      onClick={(e) => {
                        e.stopPropagation();
                        onOpen(t);
                      }}
                    >
                      {t.title}
                    </button>
                    {(showProject || t.description) && (
                      <div className="task-meta-line">
                        {showProject && <span className="font-medium">{t.projectName}</span>}
                        {showProject && t.description && <span aria-hidden="true">·</span>}
                        {t.description && <span className="truncate">{t.description}</span>}
                      </div>
                    )}
                  </div>
                </td>
                <td onClick={(e) => e.stopPropagation()}>
                  <select
                    value={t.status}
                    onChange={(e) => onStatusChange(t, e.target.value as TaskStatus)}
                    aria-label={`Status of ${t.title}`}
                    className={`status-select is-${t.status.toLowerCase()}`}
                  >
                    {STATUS_OPTIONS.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <PriorityBadge priority={t.priority} />
                </td>
                <td>
                  {t.assigneeId ? (
                    <span className="assignee">
                      <Avatar name={t.assigneeName || '?'} size="xs" />
                      {t.assigneeName || 'Unknown'}
                    </span>
                  ) : (
                    <span className="text-faint text-sm">Unassigned</span>
                  )}
                </td>
                <td>{t.dueDate ? <DueDateLabel task={t} /> : <span className="text-faint text-sm">No date</span>}</td>
              </tr>
            ))}
          </tbody>
        </Table>
        </>
      )}
      {bulk?.remove && (
        <ConfirmDialog
          isOpen={confirmBulkDelete}
          title="Delete tasks"
          message={`Delete ${selectedTasks.length} task${selectedTasks.length === 1 ? '' : 's'}? Their comments and files are deleted too. This cannot be undone.`}
          confirmText="Delete tasks"
          isDestructive
          isLoading={bulk.busy}
          onConfirm={async () => {
            await applyBulk(() => bulk.remove!(selectedTasks));
            setConfirmBulkDelete(false);
          }}
          onCancel={() => setConfirmBulkDelete(false)}
        />
      )}
    </div>
  );
};
