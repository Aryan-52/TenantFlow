import { useEffect, useRef } from 'react';

interface Shortcuts {
  /** Ctrl/Cmd+K, or "/" outside text fields. */
  onOpenPalette: () => void;
  /** "g" followed by a key within 1 s, outside text fields (e.g. g d = dashboard). */
  onGoTo: (key: string) => void;
  /** "c" outside text fields and dialogs: create a task. */
  onCreate?: () => void;
}

function isEditable(target: EventTarget | null): boolean {
  const el = target as HTMLElement | null;
  if (!el) return false;
  return el.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(el.tagName);
}

/** App-wide keyboard shortcuts. Plain-letter shortcuts never fire while typing. */
export function useGlobalShortcuts({ onOpenPalette, onGoTo, onCreate }: Shortcuts) {
  const pendingG = useRef<number | null>(null);
  const handlers = useRef({ onOpenPalette, onGoTo, onCreate });
  useEffect(() => {
    handlers.current = { onOpenPalette, onGoTo, onCreate };
  });

  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        handlers.current.onOpenPalette();
        return;
      }
      if (e.ctrlKey || e.metaKey || e.altKey || isEditable(e.target)) return;

      if (pendingG.current !== null) {
        window.clearTimeout(pendingG.current);
        pendingG.current = null;
        handlers.current.onGoTo(e.key.toLowerCase());
        return;
      }
      if (e.key === '/') {
        e.preventDefault();
        handlers.current.onOpenPalette();
      } else if (e.key === 'c' && handlers.current.onCreate && !document.querySelector('[role="dialog"]')) {
        e.preventDefault();
        handlers.current.onCreate();
      } else if (e.key === 'g') {
        pendingG.current = window.setTimeout(() => {
          pendingG.current = null;
        }, 1000);
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);
}
