import type { ReactNode } from 'react';

export interface BadgeProps {
  color?: 'blue' | 'green' | 'red' | 'yellow' | 'gray';
  children: ReactNode;
}

export const Badge = ({ color = 'gray', children }: BadgeProps) => {
  return (
    <span className={`badge badge-${color}`}>
      {children}
    </span>
  );
};
