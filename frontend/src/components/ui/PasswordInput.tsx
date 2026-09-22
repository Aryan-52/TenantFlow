import React, { useState } from 'react';
import { Eye, EyeOff, Lock } from 'lucide-react';
import { Input, type InputProps } from './Input';

export const PasswordInput = React.forwardRef<HTMLInputElement, Omit<InputProps, 'type' | 'trailingIcon'>>(
  (props, ref) => {
    const [visible, setVisible] = useState(false);
    return (
      <Input
        ref={ref}
        type={visible ? 'text' : 'password'}
        icon={<Lock size={15} />}
        trailingIcon={
          <button
            type="button"
            onClick={() => setVisible((v) => !v)}
            className="btn btn-ghost btn-icon"
            style={{ padding: '0.25rem' }}
            aria-label={visible ? 'Hide password' : 'Show password'}
            tabIndex={-1}
          >
            {visible ? <EyeOff size={16} /> : <Eye size={16} />}
          </button>
        }
        {...props}
      />
    );
  }
);
PasswordInput.displayName = 'PasswordInput';
