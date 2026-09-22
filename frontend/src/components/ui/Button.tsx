import React, { type ButtonHTMLAttributes } from 'react';
import { Loader2 } from 'lucide-react';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'danger-outline' | 'ghost';
  size?: 'sm' | 'md';
  isLoading?: boolean;
  fullWidth?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className = '', variant = 'primary', size = 'md', isLoading = false, fullWidth = false, children, disabled, ...props }, ref) => {
    const classes = [
      'btn',
      `btn-${variant}`,
      size === 'sm' ? 'btn-sm' : '',
      fullWidth ? 'btn-block' : '',
      className,
    ].filter(Boolean).join(' ');

    return (
      <button ref={ref} className={classes} disabled={isLoading || disabled} {...props}>
        {isLoading && <Loader2 className="animate-spin" size={size === 'sm' ? 14 : 16} />}
        {children}
      </button>
    );
  }
);
Button.displayName = 'Button';
