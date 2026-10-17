import type { AppNotification } from '../../api/activity';
import { timeAgo } from '../../api/activity';

interface NotificationItemProps {
  notification: AppNotification;
  onOpen: (n: AppNotification) => void;
}

/** One notification row; the whole row is a single button (no nested controls). */
export const NotificationItem = ({ notification: n, onOpen }: NotificationItemProps) => (
  <button
    type="button"
    className={`notification-item ${n.read ? '' : 'is-unread'}`.trim()}
    onClick={() => onOpen(n)}
    aria-label={`${n.read ? '' : 'Unread: '}${n.title}${n.body ? `. ${n.body}` : ''}`}
  >
    <span className="notification-dot" aria-hidden="true" />
    <span className="notification-text">
      <span className="notification-title">{n.title}</span>
      {n.body && <span className="notification-body">{n.body}</span>}
      <span className="notification-time">{timeAgo(n.createdAt)}</span>
    </span>
  </button>
);
