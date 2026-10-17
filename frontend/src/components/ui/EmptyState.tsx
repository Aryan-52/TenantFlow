import type { ReactNode } from 'react';

interface EmptyStateProps {
  icon?: ReactNode;
  /** A larger illustration, used instead of the icon for first-run states. */
  illustration?: ReactNode;
  title: string;
  description: string;
  action?: ReactNode;
  /** "plain": dashed outline without a card, for empty sections inside another card. */
  variant?: 'card' | 'plain';
}

/** Explains what is empty and what the user can do next. */
export const EmptyState = ({ icon, illustration, title, description, action, variant = 'card' }: EmptyStateProps) => (
  <div className={`empty-state ${variant === 'card' ? 'card' : 'is-plain rounded-lg'}`}>
    {illustration ? (
      <div className="empty-state-illustration" aria-hidden="true">{illustration}</div>
    ) : (
      icon && <div className="empty-state-icon" aria-hidden="true">{icon}</div>
    )}
    <div className="empty-state-title">{title}</div>
    <p className="empty-state-description">{description}</p>
    {action && <div className="flex gap-2 justify-center flex-wrap">{action}</div>}
  </div>
);
