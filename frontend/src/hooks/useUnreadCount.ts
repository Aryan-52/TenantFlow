import { useCallback, useEffect, useState } from 'react';
import { notificationApi } from '../api/activity';

const POLL_MS = 30_000;

/**
 * Unread notification count, refreshed every 30 s while the tab is visible and whenever
 * the window regains focus. Errors keep the last known value (no noisy UI).
 */
export function useUnreadCount(enabled: boolean) {
  const [count, setCount] = useState(0);

  const refresh = useCallback(async () => {
    try {
      setCount(await notificationApi.unreadCount());
    } catch {
      // Keep the previous count; the next poll retries.
    }
  }, []);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    const tick = () => {
      if (!cancelled && document.visibilityState === 'visible') void refresh();
    };
    tick();
    const timer = window.setInterval(tick, POLL_MS);
    window.addEventListener('focus', tick);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
      window.removeEventListener('focus', tick);
    };
  }, [enabled, refresh]);

  return { count, setCount, refresh };
}
