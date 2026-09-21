import type { ReactNode } from 'react';

interface EmptyStateProps {
  icon?: ReactNode;
  title: string;
  description: string;
  action?: ReactNode;
}

export const EmptyState = ({ icon, title, description, action }: EmptyStateProps) => {
  return (
    <div className="flex flex-col items-center justify-center p-6 text-center card" style={{ minHeight: '300px' }}>
      {icon && <div className="mb-4 text-muted">{icon}</div>}
      <h3 className="mb-2">{title}</h3>
      <p className="mb-6 max-w-md">{description}</p>
      {action && <div>{action}</div>}
    </div>
  );
};
