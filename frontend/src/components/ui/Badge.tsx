import type { ReactNode } from 'react';
import { AlertTriangle } from 'lucide-react';
import type { ProjectStatus, TaskPriority, TaskStatus } from '../../api/work';
import { PRIORITY_LABELS, PROJECT_STATUS_LABELS, STATUS_LABELS } from '../tasks/taskMeta';

export interface BadgeProps {
  color?: 'blue' | 'green' | 'red' | 'yellow' | 'gray' | 'urgent';
  children: ReactNode;
  className?: string;
}

export const Badge = ({ color = 'gray', children, className = '' }: BadgeProps) => {
  return <span className={`badge badge-${color} ${className}`.trim()}>{children}</span>;
};

/** Maps a tenant membership Role to a consistent badge color across the app. */
export const RoleBadge = ({ role }: { role: 'OWNER' | 'ADMIN' | 'MEMBER' }) => {
  const color = role === 'OWNER' ? 'blue' : role === 'ADMIN' ? 'yellow' : 'gray';
  return <Badge color={color}>{role.charAt(0) + role.slice(1).toLowerCase()}</Badge>;
};

/** Maps a Task status to a consistent badge color across the app. */
export const StatusBadge = ({ status }: { status: TaskStatus }) => {
  const color = status === 'DONE' ? 'green' : status === 'IN_PROGRESS' ? 'blue' : 'gray';
  return <Badge color={color} className="badge-status">{STATUS_LABELS[status]}</Badge>;
};

/**
 * Task priority as signal bars + label: readable at a glance without a wall of coloured
 * pills. Urgent gets a warning icon instead of bars.
 */
export const PriorityBadge = ({ priority }: { priority: TaskPriority }) => {
  const level = priority.toLowerCase();
  return (
    <span className={`priority priority-${level}`}>
      {priority === 'URGENT' ? (
        <AlertTriangle size={12} aria-hidden="true" />
      ) : (
        <span className="priority-bars" aria-hidden="true">
          <span />
          <span />
          <span />
        </span>
      )}
      {PRIORITY_LABELS[priority]}
    </span>
  );
};

const PROJECT_STATUS_COLORS: Record<ProjectStatus, BadgeProps['color']> = {
  ACTIVE: 'green',
  ON_HOLD: 'yellow',
  COMPLETED: 'blue',
  ARCHIVED: 'gray',
};

export const ProjectStatusBadge = ({ status }: { status: ProjectStatus }) => {
  return <Badge color={PROJECT_STATUS_COLORS[status]} className="badge-status">{PROJECT_STATUS_LABELS[status]}</Badge>;
};
