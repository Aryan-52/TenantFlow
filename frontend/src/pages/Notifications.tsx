import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell, CheckCheck } from 'lucide-react';
import { apiError } from '../api/work';
import { notificationApi, type AppNotification, type NotificationCategory } from '../api/activity';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { EmptyState } from '../components/ui/EmptyState';
import { ErrorState } from '../components/ui/ErrorState';
import { Skeleton } from '../components/ui/Skeleton';
import { NotificationItem } from '../components/notifications/NotificationItem';

const PAGE_SIZE = 20;

const CATEGORIES: { value: NotificationCategory; label: string }[] = [
  { value: 'all', label: 'Everything' },
  { value: 'assigned', label: 'Assigned' },
  { value: 'mentions', label: 'Mentions and comments' },
  { value: 'reminders', label: 'Reminders' },
  { value: 'updates', label: 'Task updates' },
  { value: 'automations', label: 'Automations' },
];

/** All of the user's notifications (across workspaces they belong to), paginated. */
const Notifications = () => {
  const navigate = useNavigate();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [category, setCategory] = useState<NotificationCategory>('all');
  const [items, setItems] = useState<AppNotification[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    notificationApi
      .list({ unreadOnly, category, page: 0, size: PAGE_SIZE })
      .then((res) => {
        if (cancelled) return;
        setItems(res.items);
        setPage(0);
        setHasNext(res.hasNext);
        setError('');
      })
      .catch((err) => {
        if (!cancelled) setError(apiError(err, 'Could not load notifications').message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [unreadOnly, category]);

  const loadMore = async () => {
    try {
      const res = await notificationApi.list({ unreadOnly, category, page: page + 1, size: PAGE_SIZE });
      setItems((prev) => [...prev, ...res.items]);
      setPage(page + 1);
      setHasNext(res.hasNext);
    } catch (err) {
      setError(apiError(err, 'Could not load more notifications').message);
    }
  };

  const open = async (n: AppNotification) => {
    if (!n.read) {
      setItems((prev) => prev.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
      notificationApi.markRead(n.id).catch(() => undefined);
    }
    if (n.link) navigate(n.link);
  };

  const markAll = async () => {
    try {
      await notificationApi.markAllRead();
      setItems((prev) => (unreadOnly ? [] : prev.map((x) => ({ ...x, read: true }))));
    } catch (err) {
      setError(apiError(err, 'Could not mark notifications as read').message);
    }
  };

  return (
    <div className="max-w-3xl">
      <PageHeader
        title="Inbox"
        subtitle="Assignments, mentions, reminders and updates that involve you, across your workspaces."
        actions={
          <Button variant="secondary" onClick={markAll} disabled={!items.some((n) => !n.read)}>
            <CheckCheck size={16} /> Mark all read
          </Button>
        }
      />
      <div className="toolbar">
      <select aria-label="Category" value={category} onChange={(e) => { setLoading(true); setCategory(e.target.value as NotificationCategory); }}>
        {CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
      </select>
      <div className="segmented" role="group" aria-label="Filter notifications">
        <button type="button" className={!unreadOnly ? 'active' : ''} aria-pressed={!unreadOnly} onClick={() => setUnreadOnly(false)}>
          All
        </button>
        <button type="button" className={unreadOnly ? 'active' : ''} aria-pressed={unreadOnly} onClick={() => setUnreadOnly(true)}>
          Unread
        </button>
      </div>
      </div>
      <ErrorState message={error} />
      {loading ? (
        <div className="flex flex-col gap-2">
          <Skeleton style={{ height: '3.5rem' }} />
          <Skeleton style={{ height: '3.5rem' }} />
          <Skeleton style={{ height: '3.5rem' }} />
        </div>
      ) : items.length === 0 ? (
        <EmptyState
          icon={<Bell size={24} />}
          title={unreadOnly ? 'Nothing unread here' : category === 'all' ? 'No notifications yet' : 'Nothing in this category'}
          description="You'll be notified when someone assigns you a task or changes work you're responsible for."
        />
      ) : (
        <div className="card notification-list">
          {items.map((n) => (
            <NotificationItem key={n.id} notification={n} onOpen={open} />
          ))}
          {hasNext && (
            <div className="p-3 text-center border-t">
              <Button variant="secondary" size="sm" onClick={loadMore}>
                Load more
              </Button>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default Notifications;
