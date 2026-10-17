import React, { useId, type SelectHTMLAttributes } from 'react';

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string;
  error?: string;
  hint?: string;
  options: { value: string; label: string }[];
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
  ({ className = '', label, error, hint, options, id, ...props }, ref) => {
    // Labels always point at their control, even when no id is passed.
    const autoId = useId();
    const controlId = id ?? autoId;
    return (
      <div className="form-group">
        {label && <label htmlFor={controlId}>{label}</label>}
        <select
          ref={ref}
          id={controlId}
          className={`${error ? 'input-error' : ''} ${className}`.trim()}
          aria-invalid={!!error}
          aria-describedby={error || hint ? `${controlId}-msg` : undefined}
          {...props}
        >
          {options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
        {error ? <div className="field-error" id={`${controlId}-msg`}>{error}</div> : hint ? <div className="field-hint" id={`${controlId}-msg`}>{hint}</div> : null}
      </div>
    );
  }
);
Select.displayName = 'Select';
