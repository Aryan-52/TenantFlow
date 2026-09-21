import React, { type ButtonHTMLAttributes } from 'react';
import { Loader2 } from 'lucide-react';

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  size?: 'sm' | 'md';
  isLoading?: boolean;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className = '', variant = 'primary', size = 'md', isLoading = false, children, disabled, ...props }, ref) => {
    const baseClass = 'btn';
    const variantClass = `btn-${variant}`;
    const sizeClass = size === 'sm' ? 'btn-sm' : '';
    const finalClass = `${baseClass} ${variantClass} ${sizeClass} ${className}`.trim();

    return (
      <button ref={ref} className={finalClass} disabled={isLoading || disabled} {...props}>
        {isLoading && <Loader2 className="animate-spin" size={size === 'sm' ? 14 : 16} />}
        {children}
      </button>
    );
  }
);
Button.displayName = 'Button';
