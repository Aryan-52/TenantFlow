import { useRef, useState, type ReactNode } from 'react';
import { useClickOutside } from '../../hooks/useClickOutside';

interface DropdownProps {
  trigger: (props: { open: boolean; toggle: () => void }) => ReactNode;
  children: ReactNode;
  align?: 'left' | 'right';
}

/** A small, self-contained dropdown menu: manages its own open state and closes on
 * outside click / Escape. Used for the workspace switcher, the user menu, and row actions. */
export const Dropdown = ({ trigger, children, align = 'right' }: DropdownProps) => {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useClickOutside(ref, () => setOpen(false), open);

  return (
    <div className="dropdown" ref={ref}>
      {trigger({ open, toggle: () => setOpen((o) => !o) })}
      {open && (
        <div className={`dropdown-menu align-${align}`} onClick={() => setOpen(false)}>
          {children}
        </div>
      )}
    </div>
  );
};

export const DropdownItem = ({
  children,
  onClick,
  danger = false,
  active = false,
  icon,
}: {
  children: ReactNode;
  onClick?: () => void;
  danger?: boolean;
  active?: boolean;
  icon?: ReactNode;
}) => (
  <button
    type="button"
    className={`dropdown-item ${danger ? 'danger' : ''} ${active ? 'active' : ''}`.trim()}
    onClick={onClick}
  >
    {icon}
    {children}
  </button>
);

export const DropdownLabel = ({ children }: { children: ReactNode }) => (
  <div className="dropdown-section-label">{children}</div>
);

export const DropdownDivider = () => <div className="dropdown-divider" />;
