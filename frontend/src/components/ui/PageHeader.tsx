import type { ReactNode } from 'react';

interface PageHeaderProps {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
}

export const PageHeader = ({ title, subtitle, actions }: PageHeaderProps) => {
  return (
    <div className="flex justify-between items-start gap-4 mb-6 flex-col sm:flex-row sm:items-center">
      <div>
        <h1 className="page-title">{title}</h1>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {actions && <div className="flex gap-2 flex-shrink-0">{actions}</div>}
    </div>
  );
};
