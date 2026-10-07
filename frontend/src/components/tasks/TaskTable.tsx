import { useMemo, useState, type ReactNode } from 'react';
import { CheckSquare } from 'lucide-react';
import type { Task, TaskStatus } from '../../api/work';
import type { Member } from '../../pages/Members';
import { Select } from '../ui/Select';
import { SearchInput } from '../ui/SearchInput';
import { PriorityBadge } from '../ui/Badge';
import { Table } from '../ui/Table';
import { EmptyState } from '../ui/EmptyState';
import { DueDateLabel } from './DueDateLabel';
import { PRIORITY_OPTIONS, PRIORITY_RANK, STATUS_OPTIONS, compareByDueThenPriority, dueState } from './taskMeta';

type SortOption = 'due' | 'priority' | 'updated' | 'title';

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
}

/** Filterable, sortable task list. Clicking a row opens the task's detail dialog. */
export const TaskTable = ({ tasks, members, onOpen, onStatusChange, emptyAction }: TaskTableProps) => {
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [priorityFilter, setPriorityFilter] = useState('');
  const [assigneeFilter, setAssigneeFilter] = useState('');
  const [sort, setSort] = useState<SortOption>('due');

  const visibleTasks = useMemo(() => {
    const q = search.trim().toLowerCase();
    const filtered = tasks.filter((t) => {
      if (q && !t.title.toLowerCase().includes(q) && !(t.description ?? '').toLowerCase().includes(q)) return false;
      if (statusFilter === 'OVERDUE') {
        if (dueState(t) !== 'overdue') return false;
      } else if (statusFilter && t.status !== statusFilter) return false;
      if (priorityFilter && t.priority !== priorityFilter) return false;
      if (assigneeFilter === 'unassigned') return !t.assigneeId;
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
  }, [tasks, search, statusFilter, priorityFilter, assigneeFilter, sort]);

  const hasActiveFilters = !!(search || statusFilter || priorityFilter || assigneeFilter);

  return (
    <div>
      <div className="flex flex-wrap gap-3 mb-4">
        <SearchInput value={search} onChange={setSearch} placeholder="Search tasks..." className="max-w-md" style={{ flex: '1 1 200px' }} />
        <Select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          options={[{ value: '', label: 'All statuses' }, ...STATUS_OPTIONS, { value: 'OVERDUE', label: 'Overdue' }]}
          aria-label="Filter by status"
          style={{ maxWidth: '160px' }}
        />
        <Select
          value={priorityFilter}
          onChange={(e) => setPriorityFilter(e.target.value)}
          options={[{ value: '', label: 'All priorities' }, ...PRIORITY_OPTIONS]}
          aria-label="Filter by priority"
          style={{ maxWidth: '160px' }}
        />
        <Select
          value={assigneeFilter}
          onChange={(e) => setAssigneeFilter(e.target.value)}
          options={[
            { value: '', label: 'All assignees' },
            { value: 'unassigned', label: 'Unassigned' },
            ...members.map((m) => ({ value: m.userId, label: m.name })),
          ]}
          aria-label="Filter by assignee"
          style={{ maxWidth: '180px' }}
        />
        <Select
          value={sort}
          onChange={(e) => setSort(e.target.value as SortOption)}
          options={SORT_OPTIONS}
          aria-label="Sort tasks"
          style={{ maxWidth: '200px' }}
        />
      </div>

      {visibleTasks.length === 0 ? (
        <EmptyState
          icon={<CheckSquare size={24} />}
          title="No tasks found"
          description={hasActiveFilters ? "We couldn't find any tasks matching your filters." : "This project doesn't have any tasks yet."}
          action={!hasActiveFilters ? emptyAction : undefined}
        />
      ) : (
        <Table>
          <thead>
            <tr>
              <th>Task</th>
              <th>Status</th>
              <th>Priority</th>
              <th>Assignee</th>
              <th>Due</th>
            </tr>
          </thead>
          <tbody>
            {visibleTasks.map((t) => (
              <tr key={t.id} className="table-row-clickable" onClick={() => onOpen(t)}>
                <td>
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
                  {t.description && <div className="text-xs text-muted mt-1 truncate max-w-xs">{t.description}</div>}
                </td>
                <td onClick={(e) => e.stopPropagation()}>
                  <select
                    value={t.status}
                    onChange={(e) => onStatusChange(t, e.target.value as TaskStatus)}
                    aria-label={`Status of ${t.title}`}
                    style={{ fontSize: 'var(--text-xs)', padding: '0.25rem 1.5rem 0.25rem 0.5rem' }}
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
                  <span className="text-sm">{t.assigneeId ? t.assigneeName || 'Unknown' : <span className="text-muted">Unassigned</span>}</span>
                </td>
                <td>{t.dueDate ? <DueDateLabel task={t} /> : <span className="text-muted text-sm">—</span>}</td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </div>
  );
};
