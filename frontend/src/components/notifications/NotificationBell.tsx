import { useCallback, useRef, useState } from 'react';
import { Bell, CheckCheck } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { apiError } from '../../api/work';
import { notificationApi, type AppNotification } from '../../api/activity';
import { useClickOutside } from '../../hooks/useClickOutside';
import { useUnreadCount } from '../../hooks/useUnreadCount';
import { Skeleton } from '../ui/Skeleton';
import { NotificationItem } from './NotificationItem';

/** Header bell: unread badge (polled), and a panel with the latest notifications. */
export const NotificationBell = () => {
  const navigate = useNavigate();
  const { count, setCount, refresh } = useUnreadCount(true);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const ref = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  const close = useCallback(() => setOpen(false), []);
  useClickOutside(ref, close, open);

  const load = async () => {
    setLoading(true);
    setError('');
    try {
      const page = await notificationApi.list({ size: 10 });
      setItems(page.items);
      void refresh();
    } catch (err) {
      setError(apiError(err, 'Could not load notifications').message);
    } finally {
      setLoading(false);
    }
  };

  const toggle = () => {
    const next = !open;
    setOpen(next);
    if (next) void load();
  };

  const openNotification = async (n: AppNotification) => {
    setOpen(false);
    if (!n.read) {
      setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
      setCount((c) => Math.max(0, c - 1));
      notificationApi.markRead(n.id).catch(() => void refresh());
    }
    if (n.link) navigate(n.link);
  };

  const markAll = async () => {
    try {
      await notificationApi.markAllRead();
      setItems((prev) => prev.map((x) => ({ ...x, read: true })));
      setCount(0);
    } catch (err) {
      setError(apiError(err, 'Could not mark notifications as read').message);
    }
  };

  const badge = count > 99 ? '99+' : String(count);

  return (
    <div className="notification-bell" ref={ref}>
      <button
        ref={triggerRef}
        type="button"
        className="btn btn-ghost btn-icon notification-trigger"
        onClick={toggle}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-label={count > 0 ? `Notifications, ${count} unread` : 'Notifications'}
      >
        <Bell size={18} />
        {count > 0 && (
          <span className="notification-badge" aria-hidden="true">
            {badge}
          </span>
        )}
      </button>

      {open && (
        <div className="notification-panel" role="dialog" aria-label="Notifications">
          <div className="notification-panel-header">
            <span className="font-semibold">Notifications</span>
            <button type="button" className="btn btn-ghost btn-sm" onClick={markAll} disabled={count === 0}>
              <CheckCheck size={14} /> Mark all read
            </button>
          </div>
          <div className="notification-panel-body">
            {loading && items.length === 0 ? (
              <div className="p-3 flex flex-col gap-2">
                <Skeleton style={{ height: '2.5rem' }} />
                <Skeleton style={{ height: '2.5rem' }} />
              </div>
            ) : error ? (
              <div className="p-4 text-sm">
                <p className="text-danger mb-2">{error}</p>
                <button type="button" className="btn btn-secondary btn-sm" onClick={load}>
                  Try again
                </button>
              </div>
            ) : items.length === 0 ? (
              <p className="p-4 text-sm text-muted text-center">You're all caught up.</p>
            ) : (
              items.map((n) => <NotificationItem key={n.id} notification={n} onOpen={openNotification} />)
            )}
          </div>
          <Link to="/notifications" className="notification-panel-footer" onClick={() => setOpen(false)}>
            View all notifications
          </Link>
        </div>
      )}
    </div>
  );
};
