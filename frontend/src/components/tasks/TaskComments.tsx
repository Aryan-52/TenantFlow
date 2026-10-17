import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { MessageSquare, Pencil, Trash2 } from 'lucide-react';
import { commentApi, COMMENT_MAX, type TaskComment } from '../../api/comments';
import { timeAgo } from '../../api/activity';
import { apiError } from '../../api/work';
import { useAuth } from '../../contexts/AuthContext';
import { Avatar } from '../ui/Avatar';
import { Button } from '../ui/Button';
import { Skeleton } from '../ui/Skeleton';
import { ErrorState } from '../ui/ErrorState';
import { ConfirmDialog } from '../ui/ConfirmDialog';
import { MentionTextarea } from './MentionTextarea';
import { mentionedIds, renderMentions, type Mentionable } from './mentions';

interface TaskCommentsProps {
  tenantId: string;
  projectId: string;
  taskId: string;
  /** Workspace admins/owners may delete anyone's comment. */
  canModerate: boolean;
  onCountChange?: (count: number) => void;
  /** Workspace members who can be @mentioned. */
  members?: Mentionable[];
}

/** A task's discussion: persisted comments, oldest first, with a composer at the bottom. */
export const TaskComments = ({ tenantId, projectId, taskId, canModerate, onCountChange, members = [] }: TaskCommentsProps) => {
  const mentionable = members;
  const { user } = useAuth();
  const [comments, setComments] = useState<TaskComment[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  const [draft, setDraft] = useState('');
  const [posting, setPosting] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editText, setEditText] = useState('');
  const [deleting, setDeleting] = useState<TaskComment | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const countRef = useRef(onCountChange);
  useEffect(() => {
    countRef.current = onCountChange;
  });

  useEffect(() => {
    const controller = new AbortController();
    commentApi
      .list(tenantId, projectId, taskId, controller.signal)
      .then((page) => {
        setComments(page.items);
        countRef.current?.(page.totalElements);
        setError('');
      })
      .catch((err) => {
        if (!controller.signal.aborted) setError(apiError(err, 'Could not load the discussion.').message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoaded(true);
      });
    return () => controller.abort();
  }, [tenantId, projectId, taskId, reload]);

  const update = (next: TaskComment[]) => {
    setComments(next);
    countRef.current?.(next.length);
  };

  const post = async () => {
    const body = draft.trim();
    if (!body || posting) return;
    setPosting(true);
    setError('');
    try {
      const created = await commentApi.create(tenantId, projectId, taskId, body, mentionedIds(body, mentionable.filter((m) => m.userId !== user?.id)));
      update([...comments, created]);
      setDraft('');
    } catch (err) {
      // The draft stays in the box so nothing is lost.
      setError(apiError(err, 'Could not post your comment.').message);
    } finally {
      setPosting(false);
    }
  };

  const saveEdit = async (c: TaskComment) => {
    const body = editText.trim();
    if (!body) return;
    try {
      const saved = await commentApi.update(tenantId, projectId, taskId, c.id, body, mentionedIds(body, mentionable.filter((m) => m.userId !== user?.id)));
      update(comments.map((x) => (x.id === saved.id ? saved : x)));
      setEditingId(null);
    } catch (err) {
      setError(apiError(err, 'Could not save your edit.').message);
    }
  };

  const confirmDelete = async () => {
    if (!deleting) return;
    setDeleteBusy(true);
    try {
      await commentApi.remove(tenantId, projectId, taskId, deleting.id);
      update(comments.filter((x) => x.id !== deleting.id));
      setDeleting(null);
    } catch (err) {
      setError(apiError(err, 'Could not delete the comment.').message);
      setDeleting(null);
    } finally {
      setDeleteBusy(false);
    }
  };

  const submitOnShortcut = (e: KeyboardEvent<HTMLTextAreaElement>, action: () => void) => {
    if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
      e.preventDefault();
      action();
    }
  };

  return (
    <div className="comments">
      <ErrorState message={error} />
      {!loaded ? (
        <div className="flex flex-col gap-3" aria-busy="true" aria-label="Loading comments">
          <Skeleton style={{ height: '3.25rem' }} />
          <Skeleton style={{ height: '3.25rem', width: '80%' }} />
        </div>
      ) : comments.length === 0 && !error ? (
        <div className="comments-empty">
          <MessageSquare size={18} aria-hidden="true" />
          <span>No comments yet. Ask a question or share an update.</span>
        </div>
      ) : error && comments.length === 0 ? (
        <Button variant="secondary" size="sm" onClick={() => { setLoaded(false); setReload((n) => n + 1); }}>
          Try again
        </Button>
      ) : (
        <ol className="comment-list" aria-label="Comments">
          {comments.map((c) => {
            const mine = user?.id != null && c.authorId === user.id;
            const name = c.authorName ?? 'Former member';
            return (
              <li key={c.id} className="comment">
                <Avatar name={name} size="sm" />
                <div className="comment-main">
                  <div className="comment-head">
                    <span className="comment-author">{name}</span>
                    <time className="comment-time" dateTime={c.createdAt} title={new Date(c.createdAt).toLocaleString()}>
                      {timeAgo(c.createdAt)}
                    </time>
                    {c.edited && <span className="comment-time">· edited</span>}
                    {(mine || canModerate) && editingId !== c.id && (
                      <span className="comment-actions">
                        {mine && (
                          <button type="button" className="icon-button" aria-label="Edit comment" data-tooltip="Edit" onClick={() => { setEditingId(c.id); setEditText(c.body); }}>
                            <Pencil size={13} />
                          </button>
                        )}
                        <button type="button" className="icon-button" aria-label="Delete comment" data-tooltip="Delete" onClick={() => setDeleting(c)}>
                          <Trash2 size={13} />
                        </button>
                      </span>
                    )}
                  </div>
                  {editingId === c.id ? (
                    <div className="comment-edit">
                      <MentionTextarea
                        members={mentionable.filter((m) => m.userId !== user?.id)}
                        value={editText}
                        onChange={setEditText}
                        onKeyDown={(e) => submitOnShortcut(e, () => saveEdit(c))}
                        maxLength={COMMENT_MAX}
                        aria-label="Edit comment"
                        autoFocus
                      />
                      <div className="flex gap-2 justify-end mt-2">
                        <Button variant="ghost" size="sm" onClick={() => setEditingId(null)}>Cancel</Button>
                        <Button size="sm" onClick={() => saveEdit(c)} disabled={!editText.trim()}>Save</Button>
                      </div>
                    </div>
                  ) : (
                    <p className="comment-body">{renderMentions(c.body, mentionable)}</p>
                  )}
                </div>
              </li>
            );
          })}
        </ol>
      )}

      <div className="comment-composer">
        {user && <Avatar name={user.name} size="sm" />}
        <div className="flex-1">
          <MentionTextarea
            members={mentionable.filter((m) => m.userId !== user?.id)}
            value={draft}
            onChange={setDraft}
            onKeyDown={(e) => submitOnShortcut(e, post)}
            placeholder="Write a comment… Type @ to mention someone"
            aria-label="Write a comment"
            maxLength={COMMENT_MAX}
            rows={2}
          />
          <div className="comment-composer-foot">
            <span className="text-xs text-faint">@ to mention · Ctrl/⌘ + Enter to send</span>
            <Button size="sm" onClick={post} isLoading={posting} disabled={!draft.trim() || posting}>
              Comment
            </Button>
          </div>
        </div>
      </div>

      <ConfirmDialog
        isOpen={!!deleting}
        title="Delete comment"
        message="Delete this comment? It will be removed for everyone."
        confirmText="Delete comment"
        isDestructive
        isLoading={deleteBusy}
        onConfirm={confirmDelete}
        onCancel={() => setDeleting(null)}
      />
    </div>
  );
};
