import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { TaskComment } from '../../api/comments';
import { TaskComments } from './TaskComments';

const { list, create, update, remove } = vi.hoisted(() => ({ list: vi.fn(), create: vi.fn(), update: vi.fn(), remove: vi.fn() }));

vi.mock('../../api/comments', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/comments')>();
  return { ...actual, commentApi: { list, create, update, remove } };
});

vi.mock('../../contexts/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'me', name: 'Mia Chen', email: 'mia@example.test' } }),
}));

const comment = (id: string, authorId: string | null, authorName: string | null, body: string): TaskComment => ({
  id,
  taskId: 't1',
  authorId,
  authorName,
  body,
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  edited: false,
});

const pageOf = (items: TaskComment[]) => ({ items, page: 0, size: 100, totalElements: items.length, totalPages: 1, hasNext: false });

const renderComments = (canModerate = false, onCountChange = vi.fn()) =>
  render(<TaskComments tenantId="ten" projectId="p1" taskId="t1" canModerate={canModerate} onCountChange={onCountChange} />);

describe('TaskComments', () => {
  beforeEach(() => {
    list.mockReset().mockResolvedValue(pageOf([comment('c1', 'other', 'Leo', 'Can you check the copy?'), comment('c2', 'me', 'Mia Chen', 'On it')]));
    create.mockReset();
    update.mockReset();
    remove.mockReset().mockResolvedValue(undefined);
  });

  it('shows the persisted discussion, oldest first, and reports the count', async () => {
    const onCount = vi.fn();
    renderComments(false, onCount);
    const items = await screen.findAllByRole('listitem');
    expect(items.map((li) => li.textContent)).toEqual([expect.stringContaining('Can you check the copy?'), expect.stringContaining('On it')]);
    expect(list).toHaveBeenCalledWith('ten', 'p1', 't1', expect.any(AbortSignal));
    expect(onCount).toHaveBeenLastCalledWith(2);
  });

  it('posts a comment and keeps the draft if posting fails', async () => {
    const user = userEvent.setup();
    create.mockRejectedValueOnce({ response: { data: { message: 'Comment cannot be empty' } } })
      .mockResolvedValueOnce(comment('c3', 'me', 'Mia Chen', 'Shipped'));
    renderComments();
    await screen.findByText('On it');
    const box = screen.getByLabelText('Write a comment');
    await user.type(box, 'Shipped');
    await user.click(screen.getByRole('button', { name: 'Comment' }));
    expect(await screen.findByText('Comment cannot be empty')).toBeTruthy();
    expect((box as HTMLTextAreaElement).value).toBe('Shipped');

    await user.click(box);
    await user.keyboard('{Control>}{Enter}{/Control}');
    expect(await screen.findByText('Shipped', { selector: '.comment-body' })).toBeTruthy();
    expect(create).toHaveBeenLastCalledWith('ten', 'p1', 't1', 'Shipped', []);
    expect((box as HTMLTextAreaElement).value).toBe('');
  });

  it('lets authors edit their own comments only; moderators may delete any', async () => {
    const user = userEvent.setup();
    update.mockResolvedValue({ ...comment('c2', 'me', 'Mia Chen', 'On it now'), edited: true });
    renderComments(false);
    const [theirs, mine] = await screen.findAllByRole('listitem');
    expect(within(theirs).queryByRole('button', { name: 'Edit comment' })).toBeNull();
    expect(within(theirs).queryByRole('button', { name: 'Delete comment' })).toBeNull();

    await user.click(within(mine).getByRole('button', { name: 'Edit comment' }));
    const editor = within(mine).getByLabelText('Edit comment');
    await user.clear(editor);
    await user.type(editor, 'On it now');
    await user.click(within(mine).getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('On it now')).toBeTruthy();
    expect(screen.getByText('· edited')).toBeTruthy();
  });

  it('asks before deleting and removes the comment', async () => {
    const user = userEvent.setup();
    renderComments(true);
    const [theirs] = await screen.findAllByRole('listitem');
    await user.click(within(theirs).getByRole('button', { name: 'Delete comment' }));
    const dialog = await screen.findByRole('dialog', { name: 'Delete comment' });
    await user.click(within(dialog).getByRole('button', { name: 'Delete comment' }));
    expect(remove).toHaveBeenCalledWith('ten', 'p1', 't1', 'c1');
    expect(screen.queryByText('Can you check the copy?')).toBeNull();
  });

  it('explains an empty discussion', async () => {
    list.mockResolvedValueOnce(pageOf([]));
    renderComments();
    expect(await screen.findByText('No comments yet. Ask a question or share an update.')).toBeTruthy();
  });
});
