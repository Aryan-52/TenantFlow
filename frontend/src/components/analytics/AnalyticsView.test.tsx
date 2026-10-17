import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Analytics } from '../../api/analytics';
import { AnalyticsView } from './AnalyticsView';

const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock('../../api/analytics', () => ({ analyticsApi: { get } }));

const DATA = {
  generatedAt: '2026-03-01T00:00:00Z',
  range: { from: '2026-02-01', to: '2026-03-01', bucket: 'DAY' },
  projects: null,
  tasks: {
    total: 10, byStatus: { TODO: 4, IN_PROGRESS: 2, DONE: 4 }, open: 6, overdue: 2, dueSoon: 1, unassignedOpen: 1,
    openWithoutDueDate: 3, completionRate: 0.4, openByPriority: { URGENT: 1, HIGH: 2, MEDIUM: 2, LOW: 1 },
  },
  workload: [{ userId: 'u1', name: 'Mia Chen', todo: 3, inProgress: 2, overdue: 1 }],
  trend: [
    { date: '2026-02-27', created: 2, completed: 1, due: 0 },
    { date: '2026-02-28', created: 0, completed: 3, due: 1 },
  ],
  delivery: {
    completed: 4, onTime: 3, late: 1, withoutDueDate: 0, onTimeRate: 0.75, averageDaysToComplete: 2.5,
    completionTrackedSince: null, untrackedDone: 2,
  },
  health: [],
  criteria: { dueSoonDays: 7, dueSoonMin: 3, staleDays: 14, concentrationMinOpen: 5, concentrationSharePercent: 60, unassignedMin: 3 },
} as unknown as Analytics;

const renderView = (projectId?: string) =>
  render(
    <MemoryRouter>
      <AnalyticsView tenantId="t1" projectId={projectId} />
    </MemoryRouter>,
  );

describe('AnalyticsView', () => {
  beforeEach(() => get.mockReset().mockResolvedValue(DATA));

  it('shows live figures, links KPIs to filtered task views and is honest about untracked history', async () => {
    renderView('p1');
    expect((await screen.findByText('Open tasks')).closest('a')?.getAttribute('href')).toBe('/tenants/t1/tasks?focus=open');
    expect(screen.getByText('75%')).toBeTruthy(); // on-time rate
    expect(screen.getByText('2.5d')).toBeTruthy();
    expect(screen.getByText(/2 tasks were completed earlier without a recorded time/)).toBeTruthy();
    expect(get).toHaveBeenCalledWith('t1', expect.objectContaining({ projectId: 'p1' }), expect.any(AbortSignal));
  });

  it('refetches for a new date range', async () => {
    const user = userEvent.setup();
    renderView();
    await screen.findByText('Open tasks');
    const before = get.mock.calls[0][1] as { from: string; to: string };
    await user.click(screen.getByRole('button', { name: /6m|6 months/i }));
    const after = get.mock.calls.at(-1)![1] as { from: string; to: string };
    expect(after.from < before.from).toBe(true);
    expect(after.to).toBe(before.to);
  });

  it('shows an empty state instead of zero-filled charts', async () => {
    get.mockResolvedValue({ ...DATA, tasks: { ...DATA.tasks, total: 0 } });
    renderView();
    expect(await screen.findByText('No tasks yet')).toBeTruthy();
  });
});
