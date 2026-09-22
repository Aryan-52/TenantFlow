import React, { type InputHTMLAttributes, type ReactNode } from 'react';

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
  icon?: ReactNode;
  trailingIcon?: ReactNode;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className = '', label, error, hint, icon, trailingIcon, id, ...props }, ref) => {
    const inputEl = (
      <input
        ref={ref}
        id={id}
        className={`${error ? 'input-error' : ''} ${className}`.trim()}
        style={trailingIcon ? { paddingRight: '2.25rem' } : undefined}
        aria-invalid={!!error}
        {...props}
      />
    );

    return (
      <div className="form-group">
        {label && <label htmlFor={id}>{label}</label>}
        {icon || trailingIcon ? (
          <div className={`input-with-icon ${icon ? 'has-leading' : ''}`.trim()}>
            {icon && <span className="input-icon">{icon}</span>}
            {inputEl}
            {trailingIcon && <span className="input-icon-trailing">{trailingIcon}</span>}
          </div>
        ) : (
          inputEl
        )}
        {error ? <div className="field-error">{error}</div> : hint ? <div className="field-hint">{hint}</div> : null}
      </div>
    );
  }
);
Input.displayName = 'Input';
