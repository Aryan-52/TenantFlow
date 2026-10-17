import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AppNotification } from '../../api/activity';
import { NotificationBell } from './NotificationBell';

const { list, unreadCount, markRead, markAllRead } = vi.hoisted(() => ({
  list: vi.fn(),
  unreadCount: vi.fn(),
  markRead: vi.fn(),
  markAllRead: vi.fn(),
}));

vi.mock('../../api/activity', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/activity')>();
  return { ...actual, notificationApi: { list, unreadCount, markRead, markAllRead } };
});

const notification = (overrides: Partial<AppNotification>): AppNotification => ({
  id: 'n1',
  type: 'TASK_ASSIGNED',
  title: 'You were assigned: Ship',
  body: 'In Apollo',
  link: '/tenants/t1/projects/p1/tasks?task=x1',
  tenantId: 't1',
  read: false,
  createdAt: new Date().toISOString(),
  ...overrides,
});

const page = (items: AppNotification[]) => ({ items, page: 0, size: 10, totalElements: items.length, totalPages: 1, hasNext: false });

const renderBell = () =>
  render(
    <MemoryRouter initialEntries={['/start']}>
      <Routes>
        <Route path="/start" element={<NotificationBell />} />
        <Route path="/tenants/t1/projects/p1/tasks" element={<h1>Task page</h1>} />
        <Route path="/notifications" element={<h1>All notifications</h1>} />
      </Routes>
    </MemoryRouter>,
  );

describe('NotificationBell', () => {
  beforeEach(() => {
    list.mockReset().mockResolvedValue(page([notification({}), notification({ id: 'n2', title: 'Old one', read: true })]));
    unreadCount.mockReset().mockResolvedValue(1);
    markRead.mockReset().mockResolvedValue(notification({ read: true }));
    markAllRead.mockReset().mockResolvedValue(1);
  });

  it('shows the unread count on the bell', async () => {
    renderBell();
    expect(await screen.findByRole('button', { name: 'Notifications, 1 unread' })).toBeTruthy();
  });

  it('caps a large unread count at 99+', async () => {
    unreadCount.mockResolvedValue(250);
    renderBell();
    await screen.findByRole('button', { name: 'Notifications, 250 unread' });
    expect(screen.getByText('99+')).toBeTruthy();
  });

  it('opens a panel with the latest notifications and navigates on click, marking it read', async () => {
    const user = userEvent.setup();
    renderBell();
    await user.click(await screen.findByRole('button', { name: /Notifications, 1 unread/ }));

    const dialog = await screen.findByRole('dialog', { name: 'Notifications' });
    expect(dialog.textContent).toContain('You were assigned: Ship');
    expect(dialog.textContent).toContain('Old one');

    await user.click(screen.getByRole('button', { name: /Unread: You were assigned: Ship/ }));
    expect(markRead).toHaveBeenCalledWith('n1');
    expect(await screen.findByRole('heading', { name: 'Task page' })).toBeTruthy();
  });

  it('marks everything read', async () => {
    const user = userEvent.setup();
    renderBell();
    await user.click(await screen.findByRole('button', { name: /Notifications, 1 unread/ }));
    await user.click(await screen.findByRole('button', { name: /Mark all read/ }));
    expect(markAllRead).toHaveBeenCalled();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Notifications' })).toBeTruthy());
  });

  it('shows an empty state and an error state with retry', async () => {
    const user = userEvent.setup();
    list.mockResolvedValueOnce(page([]));
    unreadCount.mockResolvedValue(0);
    renderBell();
    await user.click(await screen.findByRole('button', { name: 'Notifications' }));
    expect(await screen.findByText("You're all caught up.")).toBeTruthy();

    await user.keyboard('{Escape}');
    list.mockRejectedValueOnce({ response: { data: { message: 'Server unavailable' } } });
    await user.click(screen.getByRole('button', { name: 'Notifications' }));
    expect(await screen.findByText('Server unavailable')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Try again' }));
    expect(await screen.findByText('You were assigned: Ship')).toBeTruthy();
  });

  it('links to the full notifications page', async () => {
    const user = userEvent.setup();
    renderBell();
    await user.click(await screen.findByRole('button', { name: /Notifications/ }));
    await user.click(await screen.findByRole('link', { name: 'View all notifications' }));
    expect(await screen.findByRole('heading', { name: 'All notifications' })).toBeTruthy();
  });
});
