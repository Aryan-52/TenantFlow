import { CalendarDays } from 'lucide-react';
import type { Task } from '../../api/work';
import { dueState, formatDueDate } from './taskMeta';

const STATE_TEXT = { overdue: 'Overdue', today: 'Due today', upcoming: '', done: '', none: '' } as const;

/** Compact due-date indicator, colored by overdue / today / upcoming / completed. */
export const DueDateLabel = ({ task }: { task: Pick<Task, 'dueDate' | 'status'> }) => {
  if (!task.dueDate) return null;
  const state = dueState(task);
  const prefix = STATE_TEXT[state];
  return (
    <span className={`due-label due-${state}`} title={prefix ? `${prefix}: ${formatDueDate(task.dueDate)}` : `Due ${formatDueDate(task.dueDate)}`}>
      <CalendarDays size={12} aria-hidden="true" />
      {formatDueDate(task.dueDate)}
      {prefix && <span className="sr-only"> ({prefix})</span>}
    </span>
  );
};
