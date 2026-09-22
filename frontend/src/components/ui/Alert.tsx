import type { ReactNode } from 'react';
import { AlertCircle, CheckCircle2, Info, AlertTriangle } from 'lucide-react';

export interface AlertProps {
  variant?: 'danger' | 'success' | 'warning' | 'info';
  children: ReactNode;
  className?: string;
}

const ICONS = {
  danger: AlertCircle,
  success: CheckCircle2,
  warning: AlertTriangle,
  info: Info,
};

export const Alert = ({ variant = 'info', children, className = '' }: AlertProps) => {
  const Icon = ICONS[variant];
  return (
    <div className={`alert alert-${variant} ${className}`.trim()} role="status">
      <Icon size={18} className="alert-icon" />
      <span>{children}</span>
    </div>
  );
};
