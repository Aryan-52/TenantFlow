import { useEffect, type RefObject } from 'react';

/** Calls `handler` when a pointer event occurs outside of `ref`'s element while `active` is true. */
export function useClickOutside(ref: RefObject<HTMLElement | null>, handler: () => void, active: boolean) {
  useEffect(() => {
    if (!active) return;

    function handlePointerDown(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        handler();
      }
    }

    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        handler();
      }
    }

    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleEscape);
    };
  }, [ref, handler, active]);
}
