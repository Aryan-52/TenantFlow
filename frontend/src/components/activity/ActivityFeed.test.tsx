import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ActivityItem } from '../../api/activity';
import { ActivityFeed } from './ActivityFeed';

const { list } = vi.hoisted(() => ({ list: vi.fn() }));

vi.mock('../../api/activity', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/activity')>();
  return { ...actual, activityApi: { list } };
});

const item = (id: string, summary: string, actorName: string | null = 'Alice'): ActivityItem => ({
  id,
  type: 'TASK_STATUS_CHANGED',
  summary,
  actorId: actorName ? 'u1' : null,
  actorName,
  projectId: 'p1',
  taskId: 'x1',
  createdAt: new Date().toISOString(),
});

describe('ActivityFeed', () => {
  beforeEach(() => {
    list.mockReset();
  });

  it('renders actor and summary, scoped to the requested project, and loads more', async () => {
    const user = userEvent.setup();
    list
      .mockResolvedValueOnce({ items: [item('a1', 'moved "Ship" to Done')], page: 0, size: 1, totalElements: 2, totalPages: 2, hasNext: true })
      .mockResolvedValueOnce({ items: [item('a2', 'created task "Ship" in Apollo', null)], page: 1, size: 1, totalElements: 2, totalPages: 2, hasNext: false });

    render(<ActivityFeed tenantId="t1" projectId="p1" pageSize={1} />);

    expect(await screen.findByText('moved "Ship" to Done')).toBeTruthy();
    expect(screen.getByText('Alice')).toBeTruthy();
    expect(list).toHaveBeenCalledWith('t1', { projectId: 'p1', page: 0, size: 1 });

    await user.click(screen.getByRole('button', { name: 'Show more' }));
    expect(await screen.findByText('created task "Ship" in Apollo')).toBeTruthy();
    expect(screen.getByText('Someone')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Show more' })).toBeNull();
  });

  it('shows an empty state', async () => {
    list.mockResolvedValue({ items: [], page: 0, size: 8, totalElements: 0, totalPages: 0, hasNext: false });
    render(<ActivityFeed tenantId="t1" />);
    expect(await screen.findByText('No activity yet.')).toBeTruthy();
  });

  it('shows an error with retry', async () => {
    const user = userEvent.setup();
    list.mockRejectedValueOnce({ response: { data: { message: 'Boom' } } })
      .mockResolvedValueOnce({ items: [item('a1', 'did a thing')], page: 0, size: 8, totalElements: 1, totalPages: 1, hasNext: false });
    render(<ActivityFeed tenantId="t1" />);
    expect(await screen.findByText('Boom')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('did a thing')).toBeTruthy();
  });
});
