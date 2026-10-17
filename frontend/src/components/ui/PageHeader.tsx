import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ChevronRight } from 'lucide-react';

export interface Crumb {
  label: string;
  to?: string;
}

interface PageHeaderProps {
  title: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
  /** Small label above the title (e.g. the workspace name). */
  eyebrow?: ReactNode;
  breadcrumbs?: Crumb[];
}

export const Breadcrumbs = ({ items, className = '' }: { items: Crumb[]; className?: string }) => (
  <nav aria-label="Breadcrumb" className={`breadcrumbs ${className}`.trim()}>
    {items.map((c, i) => {
      const last = i === items.length - 1;
      return (
        <span key={`${c.label}-${i}`} className="flex items-center gap-1 truncate">
          {c.to && !last ? (
            <Link to={c.to} className="truncate">{c.label}</Link>
          ) : (
            <span className="truncate" aria-current={last ? 'page' : undefined}>{c.label}</span>
          )}
          {!last && <ChevronRight size={13} aria-hidden="true" />}
        </span>
      );
    })}
  </nav>
);

/** Consistent page heading: optional breadcrumbs/eyebrow, title, supporting text, actions. */
export const PageHeader = ({ title, subtitle, actions, eyebrow, breadcrumbs }: PageHeaderProps) => (
  <div className="page-header">
    <div className="page-header-text">
      {breadcrumbs && breadcrumbs.length > 0 && <Breadcrumbs items={breadcrumbs} />}
      {eyebrow && <div className="eyebrow mb-2">{eyebrow}</div>}
      <h1 className="page-title">{title}</h1>
      {subtitle && <p className="page-subtitle">{subtitle}</p>}
    </div>
    {actions && <div className="page-header-actions">{actions}</div>}
  </div>
);
