import type { ReactNode } from 'react';

export interface BadgeProps {
  color?: 'blue' | 'green' | 'red' | 'yellow' | 'gray';
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
export const StatusBadge = ({ status }: { status: 'TODO' | 'IN_PROGRESS' | 'DONE' }) => {
  const color = status === 'DONE' ? 'green' : status === 'IN_PROGRESS' ? 'blue' : 'gray';
  const label = status === 'IN_PROGRESS' ? 'In Progress' : status.charAt(0) + status.slice(1).toLowerCase();
  return <Badge color={color}>{label}</Badge>;
};

/** Maps a Task priority to a consistent badge color across the app. */
export const PriorityBadge = ({ priority }: { priority: 'LOW' | 'MEDIUM' | 'HIGH' }) => {
  const color = priority === 'HIGH' ? 'red' : priority === 'MEDIUM' ? 'yellow' : 'gray';
  return <Badge color={color}>{priority.charAt(0) + priority.slice(1).toLowerCase()}</Badge>;
};
