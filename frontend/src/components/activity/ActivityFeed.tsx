import { useEffect, useState } from 'react';
import { History } from 'lucide-react';
import { apiError } from '../../api/work';
import { activityApi, timeAgo, type ActivityItem } from '../../api/activity';
import { Skeleton } from '../ui/Skeleton';
import { Button } from '../ui/Button';

interface ActivityFeedProps {
  tenantId: string;
  /** Narrow to one project's history. */
  projectId?: string;
  pageSize?: number;
}

/** Workspace or project activity history, newest first, with "load more". */
export const ActivityFeed = ({ tenantId, projectId, pageSize = 8 }: ActivityFeedProps) => {
  const [items, setItems] = useState<ActivityItem[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    activityApi
      .list(tenantId, { projectId, page: 0, size: pageSize })
      .then((res) => {
        if (cancelled) return;
        setItems(res.items ?? []);
        setPage(0);
        setHasNext(res.hasNext);
        setError('');
      })
      .catch((err) => {
        if (!cancelled) setError(apiError(err, 'Could not load activity').message);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId, projectId, pageSize, reloadToken]);

  const loadMore = async () => {
    try {
      const res = await activityApi.list(tenantId, { projectId, page: page + 1, size: pageSize });
      setItems((prev) => [...prev, ...(res.items ?? [])]);
      setPage(page + 1);
      setHasNext(res.hasNext);
    } catch (err) {
      setError(apiError(err, 'Could not load more activity').message);
    }
  };

  if (loading) {
    return (
      <div className="flex flex-col gap-2">
        <Skeleton style={{ height: '1.25rem' }} />
        <Skeleton style={{ height: '1.25rem', width: '80%' }} />
        <Skeleton style={{ height: '1.25rem', width: '60%' }} />
      </div>
    );
  }
  if (error && items.length === 0) {
    return (
      <div className="text-sm">
        <p className="text-danger mb-2">{error}</p>
        <Button size="sm" variant="secondary" onClick={() => setReloadToken((t) => t + 1)}>
          Try again
        </Button>
      </div>
    );
  }
  if (items.length === 0) {
    return (
      <p className="text-sm text-muted flex items-center gap-2">
        <History size={14} /> No activity yet.
      </p>
    );
  }

  return (
    <div>
      <ol className="activity-feed">
        {items.map((a) => (
          <li key={a.id} className="activity-item">
            <span className="activity-text">
              <strong>{a.actorName ?? 'Someone'}</strong> {a.summary}
            </span>
            <time className="activity-time" dateTime={a.createdAt} title={new Date(a.createdAt).toLocaleString()}>
              {timeAgo(a.createdAt)}
            </time>
          </li>
        ))}
      </ol>
      {hasNext && (
        <Button size="sm" variant="ghost" onClick={loadMore}>
          Show more
        </Button>
      )}
    </div>
  );
};
