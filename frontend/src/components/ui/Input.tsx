import React, { useId, type InputHTMLAttributes, type ReactNode } from 'react';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
  icon?: ReactNode;
  trailingIcon?: ReactNode;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className = '', label, error, hint, icon, trailingIcon, id, ...props }, ref) => {
    // Labels always point at their control, even when no id is passed.
    const autoId = useId();
    const controlId = id ?? autoId;
    const inputEl = (
      <input
        ref={ref}
        id={controlId}
        className={`${error ? 'input-error' : ''} ${className}`.trim()}
        style={trailingIcon ? { paddingRight: '2.25rem' } : undefined}
        aria-invalid={!!error}
        aria-describedby={error || hint ? `${controlId}-msg` : undefined}
        {...props}
      />
    );

    return (
      <div className="form-group">
        {label && <label htmlFor={controlId}>{label}</label>}
        {icon || trailingIcon ? (
          <div className={`input-with-icon ${icon ? 'has-leading' : ''}`.trim()}>
            {icon && <span className="input-icon">{icon}</span>}
            {inputEl}
            {trailingIcon && <span className="input-icon-trailing">{trailingIcon}</span>}
          </div>
        ) : (
          inputEl
        )}
        {error ? <div className="field-error" id={`${controlId}-msg`}>{error}</div> : hint ? <div className="field-hint" id={`${controlId}-msg`}>{hint}</div> : null}
      </div>
    );
  }
);
Input.displayName = 'Input';
