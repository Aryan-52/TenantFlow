import type { ReactNode } from 'react';
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
  return <Badge color={color}>{STATUS_LABELS[status]}</Badge>;
};

const PRIORITY_COLORS: Record<TaskPriority, BadgeProps['color']> = {
  LOW: 'gray',
  MEDIUM: 'yellow',
  HIGH: 'red',
  URGENT: 'urgent',
};

/** Maps a Task priority to a consistent badge color across the app. */
export const PriorityBadge = ({ priority }: { priority: TaskPriority }) => {
  return <Badge color={PRIORITY_COLORS[priority]}>{PRIORITY_LABELS[priority]}</Badge>;
};

const PROJECT_STATUS_COLORS: Record<ProjectStatus, BadgeProps['color']> = {
  ACTIVE: 'green',
  ON_HOLD: 'yellow',
  COMPLETED: 'blue',
  ARCHIVED: 'gray',
};

export const ProjectStatusBadge = ({ status }: { status: ProjectStatus }) => {
  return <Badge color={PROJECT_STATUS_COLORS[status]}>{PROJECT_STATUS_LABELS[status]}</Badge>;
};
