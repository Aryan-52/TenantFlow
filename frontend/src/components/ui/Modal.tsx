import { useEffect, useRef, type ReactNode } from 'react';
import { X } from 'lucide-react';

export interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  footer?: ReactNode;
  size?: 'sm' | 'md' | 'lg';
  /** "drawer": a full-height panel from the right that keeps the page visible beside it. */
  variant?: 'dialog' | 'drawer';
  /** Shown next to the title (e.g. a breadcrumb or status). */
  eyebrow?: ReactNode;
}

/**
 * Dialog: closes on Escape and backdrop click, moves focus into the dialog when it opens
 * (unless a child already took it, e.g. an autoFocus field) and returns focus afterwards.
 */
export const Modal = ({ isOpen, onClose, title, children, footer, size = 'md', variant = 'dialog', eyebrow }: ModalProps) => {
  const dialogRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  });

  useEffect(() => {
    if (!isOpen) return;
    const previous = document.activeElement as HTMLElement | null;
    const dialog = dialogRef.current;
    if (dialog && !dialog.contains(document.activeElement)) dialog.focus();
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !e.defaultPrevented) {
        e.stopPropagation();
        onCloseRef.current();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      if (previous && document.contains(previous)) previous.focus?.();
    };
  }, [isOpen]);

  if (!isOpen) return null;

  const sizeClass = size === 'sm' ? 'modal-sm' : size === 'lg' ? 'modal-lg' : '';

  return (
    <div className={`modal-backdrop ${variant === 'drawer' ? 'is-drawer' : ''}`.trim()} onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div
        ref={dialogRef}
        className={`modal-content ${sizeClass} ${variant === 'drawer' ? 'is-drawer' : ''}`.replace(/\s+/g, ' ').trim()}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        tabIndex={-1}
        style={{ outline: 'none' }}
      >
        <div className="modal-header">
          <div style={{ minWidth: 0 }}>
            {eyebrow && <div className="modal-eyebrow">{eyebrow}</div>}
            <h3>{title}</h3>
          </div>
          <button type="button" className="icon-button" onClick={onClose} aria-label="Close dialog">
            <X size={18} />
          </button>
        </div>
        <div className="modal-body">{children}</div>
        {footer && <div className="modal-footer">{footer}</div>}
      </div>
    </div>
  );
};
