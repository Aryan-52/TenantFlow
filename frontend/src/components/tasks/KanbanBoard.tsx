import { useMemo, useState, type DragEvent } from 'react';
import { ChevronLeft, ChevronRight, Plus, User } from 'lucide-react';
import type { Task, TaskStatus } from '../../api/work';
import { PriorityBadge } from '../ui/Badge';
import { DueDateLabel } from './DueDateLabel';
import { STATUS_LABELS, TASK_STATUSES, compareByDueThenPriority } from './taskMeta';

interface KanbanBoardProps {
  tasks: Task[];
  /** Ids of tasks with a status change in flight. */
  pendingIds: Set<string>;
  onOpen: (task: Task) => void;
  onMove: (task: Task, status: TaskStatus) => void;
  onCreate: (status: TaskStatus) => void;
}

const DRAG_TYPE = 'application/x-tenantflow-task';

/**
 * Tasks grouped into one column per status. Cards can be dragged between columns
 * (native HTML5 drag and drop, desktop) or moved with the arrow buttons on each card,
 * which also covers touch devices and keyboard users.
 */
export const KanbanBoard = ({ tasks, pendingIds, onOpen, onMove, onCreate }: KanbanBoardProps) => {
  const [draggingId, setDraggingId] = useState<string | null>(null);
  const [dropTarget, setDropTarget] = useState<TaskStatus | null>(null);

  const columns = useMemo(() => {
    const byStatus: Record<TaskStatus, Task[]> = { TODO: [], IN_PROGRESS: [], DONE: [] };
    tasks.forEach((t) => byStatus[t.status].push(t));
    TASK_STATUSES.forEach((s) => byStatus[s].sort(compareByDueThenPriority));
    return byStatus;
  }, [tasks]);

  const taskById = (id: string) => tasks.find((t) => t.id === id);

  const handleDragStart = (e: DragEvent, task: Task) => {
    e.dataTransfer.setData(DRAG_TYPE, task.id);
    e.dataTransfer.effectAllowed = 'move';
    setDraggingId(task.id);
  };

  const handleDragEnd = () => {
    setDraggingId(null);
    setDropTarget(null);
  };

  const handleDragOver = (e: DragEvent, status: TaskStatus) => {
    // Only accept our own cards - not files or text dragged in from elsewhere.
    if (!e.dataTransfer.types.includes(DRAG_TYPE)) return;
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
    if (dropTarget !== status) setDropTarget(status);
  };

  const handleDrop = (e: DragEvent, status: TaskStatus) => {
    e.preventDefault();
    const task = taskById(e.dataTransfer.getData(DRAG_TYPE));
    handleDragEnd();
    if (task && task.status !== status && !pendingIds.has(task.id)) onMove(task, status);
  };

  return (
    <div className="kanban-board">
      {TASK_STATUSES.map((status, columnIndex) => {
        const columnTasks = columns[status];
        const prev = TASK_STATUSES[columnIndex - 1];
        const next = TASK_STATUSES[columnIndex + 1];
        return (
          <section
            key={status}
            className={`kanban-column ${dropTarget === status ? 'is-drop-target' : ''}`.trim()}
            aria-label={`${STATUS_LABELS[status]} column`}
            onDragOver={(e) => handleDragOver(e, status)}
            onDragLeave={(e) => {
              if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDropTarget(null);
            }}
            onDrop={(e) => handleDrop(e, status)}
          >
            <header className="kanban-column-header">
              <span className={`kanban-dot kanban-dot-${status.toLowerCase()}`} aria-hidden="true" />
              <h3 className="kanban-column-title">{STATUS_LABELS[status]}</h3>
              <span className="kanban-count">{columnTasks.length}</span>
              <button
                type="button"
                className="btn btn-ghost btn-icon btn-sm ml-auto"
                onClick={() => onCreate(status)}
                aria-label={`Add task to ${STATUS_LABELS[status]}`}
                title="Add task"
              >
                <Plus size={15} />
              </button>
            </header>

            <div className="kanban-cards">
              {columnTasks.length === 0 ? (
                <p className="kanban-empty">{draggingId ? 'Drop here' : 'No tasks'}</p>
              ) : (
                columnTasks.map((task) => {
                  const pending = pendingIds.has(task.id);
                  return (
                    <article
                      key={task.id}
                      className={`kanban-card ${draggingId === task.id ? 'is-dragging' : ''} ${pending ? 'is-pending' : ''}`.trim()}
                      draggable={!pending}
                      onDragStart={(e) => handleDragStart(e, task)}
                      onDragEnd={handleDragEnd}
                    >
                      <button type="button" className="kanban-card-title" onClick={() => onOpen(task)}>
                        {task.title}
                      </button>
                      <div className="flex items-center gap-2 flex-wrap mt-2">
                        <PriorityBadge priority={task.priority} />
                        <DueDateLabel task={task} />
                      </div>
                      <div className="kanban-card-footer">
                        <span className="text-xs text-muted flex items-center gap-1 truncate" style={{ minWidth: 0 }}>
                          <User size={12} aria-hidden="true" />
                          {task.assigneeId ? task.assigneeName || 'Unknown' : 'Unassigned'}
                        </span>
                        <span className="flex gap-1 flex-shrink-0">
                          {prev && (
                            <button
                              type="button"
                              className="btn btn-ghost btn-icon btn-sm"
                              disabled={pending}
                              onClick={() => onMove(task, prev)}
                              aria-label={`Move "${task.title}" to ${STATUS_LABELS[prev]}`}
                              title={`Move to ${STATUS_LABELS[prev]}`}
                            >
                              <ChevronLeft size={14} />
                            </button>
                          )}
                          {next && (
                            <button
                              type="button"
                              className="btn btn-ghost btn-icon btn-sm"
                              disabled={pending}
                              onClick={() => onMove(task, next)}
                              aria-label={`Move "${task.title}" to ${STATUS_LABELS[next]}`}
                              title={`Move to ${STATUS_LABELS[next]}`}
                            >
                              <ChevronRight size={14} />
                            </button>
                          )}
                        </span>
                      </div>
                    </article>
                  );
                })
              )}
            </div>
          </section>
        );
      })}
    </div>
  );
};
