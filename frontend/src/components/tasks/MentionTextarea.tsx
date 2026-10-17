import { useId, useRef, useState, type KeyboardEvent, type TextareaHTMLAttributes } from 'react';
import type { Mentionable } from './mentions';
import { Avatar } from '../ui/Avatar';


interface MentionTextareaProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, 'value' | 'onChange'> {
  value: string;
  onChange: (value: string) => void;
  members: Mentionable[];
}

/** The "@query" being typed right before the caret, if any. */
function activeQuery(text: string, caret: number): { start: number; query: string } | null {
  const before = text.slice(0, caret);
  const at = before.lastIndexOf('@');
  if (at < 0 || (at > 0 && !/\s/.test(before[at - 1]))) return null;
  const query = before.slice(at + 1);
  if (query.length > 30 || /\n/.test(query)) return null;
  return { start: at, query };
}

/**
 * A textarea that suggests workspace members after "@" and inserts "@Name". Arrow keys move,
 * Enter or Tab picks, Escape closes the list without leaving the field.
 */
export const MentionTextarea = ({ value, onChange, members, onKeyDown, ...props }: MentionTextareaProps) => {
  const ref = useRef<HTMLTextAreaElement>(null);
  const listId = useId();
  const [state, setState] = useState<{ start: number; query: string } | null>(null);
  const [active, setActive] = useState(0);

  const options = state
    ? members.filter((m) => m.name.toLowerCase().includes(state.query.toLowerCase())).slice(0, 6)
    : [];
  const open = !!state && options.length > 0;

  const sync = (text: string, caret: number) => {
    setState(activeQuery(text, caret));
    setActive(0);
  };

  const pick = (m: Mentionable) => {
    if (!state || !ref.current) return;
    const caret = ref.current.selectionStart ?? value.length;
    const next = `${value.slice(0, state.start)}@${m.name} ${value.slice(caret)}`;
    onChange(next);
    setState(null);
    const pos = state.start + m.name.length + 2;
    requestAnimationFrame(() => ref.current?.setSelectionRange(pos, pos));
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (open) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        setActive((a) => (a + (e.key === 'ArrowDown' ? 1 : options.length - 1)) % options.length);
        return;
      }
      if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault();
        pick(options[active]);
        return;
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation(); // keep the drawer open
        setState(null);
        return;
      }
    }
    onKeyDown?.(e);
  };

  return (
    <div style={{ position: 'relative' }}>
      <textarea
        {...props}
        ref={ref}
        value={value}
        onChange={(e) => {
          onChange(e.target.value);
          sync(e.target.value, e.target.selectionStart ?? e.target.value.length);
        }}
        onKeyDown={handleKeyDown}
        onBlur={() => setState(null)}
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-activedescendant={open ? `${listId}-${active}` : undefined}
      />
      {open && (
        <ul className="mention-menu" role="listbox" id={listId} aria-label="Mention a member">
          {options.map((m, i) => (
            <li
              key={m.userId}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={i === active}
              onMouseDown={(e) => {
                e.preventDefault();
                pick(m);
              }}
            >
              <Avatar name={m.name} size="xs" /> {m.name}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};
