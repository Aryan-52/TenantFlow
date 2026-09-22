import React, { type SelectHTMLAttributes } from 'react';

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  hint?: string;
  options: { value: string; label: string }[];
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ className = '', label, error, hint, options, id, ...props }, ref) => {
    return (
      <div className="form-group">
        {label && <label htmlFor={id}>{label}</label>}
        <select
          ref={ref}
          id={id}
          className={`${error ? 'input-error' : ''} ${className}`.trim()}
          aria-invalid={!!error}
          {...props}
        >
          {options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
        {error ? <div className="field-error">{error}</div> : hint ? <div className="field-hint">{hint}</div> : null}
      </div>
    );
  }
);
Select.displayName = 'Select';
