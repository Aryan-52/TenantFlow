import { useEffect, useState } from 'react';
import { activityApi, timeAgo, type ActivityItem } from '../../api/activity';
import { apiError } from '../../api/work';
import { Skeleton } from '../ui/Skeleton';
import { ErrorState } from '../ui/ErrorState';

/** Everything that happened to one task, newest first (recorded server-side). */
export const TaskHistory = ({ tenantId, taskId }: { tenantId: string; taskId: string }) => {
  const [items, setItems] = useState<ActivityItem[] | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    let cancelled = false;
    activityApi
      .list(tenantId, { taskId, size: 30 })
      .then((page) => {
        if (!cancelled) setItems(page.items ?? []);
      })
      .catch((err) => {
        if (!cancelled) setError(apiError(err, 'Could not load the history.').message);
      });
    return () => {
      cancelled = true;
    };
  }, [tenantId, taskId]);

  if (error) return <ErrorState message={error} />;
  if (!items) {
    return (
      <div className="flex flex-col gap-2" aria-busy="true" aria-label="Loading history">
        <Skeleton style={{ height: '1.25rem' }} />
        <Skeleton style={{ height: '1.25rem', width: '70%' }} />
      </div>
    );
  }
  if (items.length === 0) return <p className="text-sm text-muted">No changes recorded yet.</p>;

  return (
    <ul className="activity-feed" aria-label="Task history">
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
    </ul>
  );
};
