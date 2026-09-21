import React, { type InputHTMLAttributes } from 'react';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className = '', label, error, ...props }, ref) => {
    return (
      <div className="form-group mb-4">
        {label && <label>{label}</label>}
        <input ref={ref} className={className} {...props} />
        {error && <div className="text-danger text-sm mt-1">{error}</div>}
      </div>
    );
  }
);
Input.displayName = 'Input';
