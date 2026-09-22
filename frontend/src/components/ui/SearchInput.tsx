import { Search, X } from 'lucide-react';
import type { CSSProperties } from 'react';

interface SearchInputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  className?: string;
  style?: CSSProperties;
}

export const SearchInput = ({ value, onChange, placeholder = 'Search...', className = '', style }: SearchInputProps) => {
  return (
    <div className={`input-with-icon has-leading ${className}`.trim()} style={style}>
      <span className="input-icon">
        <Search size={16} />
      </span>
      <input
        type="text"
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        style={{ paddingRight: value ? '2.25rem' : undefined }}
        aria-label={placeholder}
      />
      {value && (
        <button
          type="button"
          className="input-icon-trailing btn btn-ghost btn-icon"
          style={{ padding: '0.25rem' }}
          onClick={() => onChange('')}
          aria-label="Clear search"
        >
          <X size={14} />
        </button>
      )}
    </div>
  );
};
