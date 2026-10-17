import { act, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { DashboardData } from '../api/dashboard';
import type { Analytics } from '../api/analytics';
import type { Task } from '../api/work';
import { addDays, toDateKey } from '../components/tasks/taskMeta';
import Dashboard from './Dashboard';

const TENANT_ID = 'tenant-123';

const { dashboardGet, listForTenant, analyticsGet, tenantState } = vi.hoisted(() => ({
  dashboardGet: vi.fn(),
  listForTenant: vi.fn(),
  analyticsGet: vi.fn(),
  tenantState: { current: { id: 'tenant-123', name: 'Acme', slug: 'acme', myRole: 'ADMIN' } },
}));

vi.mock('../api/dashboard', () => ({ dashboardApi: { get: dashboardGet } }));
vi.mock('../api/analytics', () => ({ analyticsApi: { get: analyticsGet } }));

vi.mock('../api/work', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/work')>();
  return { ...actual, taskApi: { ...actual.taskApi, listForTenant } };
});

// ActivityFeed's own request; an empty page keeps it quiet.
vi.mock('../api/client', () => ({
  default: {
    get: vi.fn(() => Promise.resolve({ data: { items: [], page: 0, size: 8, totalElements: 0, totalPages: 0, hasNext: false } })),
  },
}));

vi.mock('../contexts/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'u1', name: 'Mia Chen', email: 'mia@example.test' } }),
}));

vi.mock('../contexts/TenantContext', () => ({
  useTenant: () => ({ currentTenant: tenantState.current, tenants: [tenantState.current], addTenant: vi.fn() }),
}));

const task = (id: string, title: string, overrides: Partial<Task> = {}): Task => ({
  id,
  projectId: 'p1',
  projectName: 'Apollo',
  title,
  description: null,
  status: 'TODO',
  priority: 'HIGH',
  assigneeId: 'u1',
  assigneeName: 'Mia',
  createdById: null,
  createdByName: null,
  dueDate: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  version: 0,
  ...overrides,
});

const DATA: DashboardData = {
  totalProjects: 4,
  totalTasks: 17,
  todoTasks: 5,
  inProgressTasks: 7,
  doneTasks: 5,
  totalMembers: 3,
  overdueTasks: 2,
  dueSoonTasks: 6,
  currentUserRole: 'ADMIN',
  myOpenTasks: 3,
  myTasks: [task('m1', 'Write launch notes')],
  upcomingTasks: [task('u1', 'Fuel check', { dueDate: '2020-01-01' })],
  recentProjects: [
    { id: 'p1', name: 'Apollo', status: 'ACTIVE', totalTasks: 4, completedTasks: 1, updatedAt: '2026-01-01T00:00:00Z', ownerName: 'Ravi Patel' },
  ],
};

const ANALYTICS = {
  projects: { total: 4, byStatus: { ACTIVE: 3, ON_HOLD: 0, COMPLETED: 1, ARCHIVED: 0 }, progress: [] },
  workload: [
    { userId: 'u1', name: 'Mia Chen', todo: 2, inProgress: 1, overdue: 1 },
    { userId: null, name: null, todo: 1, inProgress: 0, overdue: 0 },
  ],
  health: [
    {
      projectId: 'p1', name: 'Apollo', status: 'ACTIVE', ownerName: 'Ravi Patel', totalTasks: 4, doneTasks: 1, openTasks: 3,
      overdue: 1, dueSoon: 0, highPriorityOpen: 0, unassignedOpen: 0, lastActivityAt: '2026-01-01T00:00:00Z',
      signals: [{ code: 'OVERDUE', severity: 'high', message: '1 overdue task' }],
    },
    {
      projectId: 'p2', name: 'Zephyr', status: 'ACTIVE', ownerName: null, totalTasks: 2, doneTasks: 2, openTasks: 0,
      overdue: 0, dueSoon: 0, highPriorityOpen: 0, unassignedOpen: 0, lastActivityAt: '2026-01-01T00:00:00Z', signals: [],
    },
  ],
  criteria: { dueSoonDays: 7, dueSoonMin: 3, staleDays: 14, concentrationMinOpen: 5, concentrationSharePercent: 60, unassignedMin: 3 },
} as unknown as Analytics;

const day = (offset: number) => toDateKey(addDays(new Date(), offset));

const page = (items: Task[]) => ({ items, page: 0, size: 50, totalElements: items.length, totalPages: 1, hasNext: false });

const Where = () => {
  const location = useLocation();
  return <h1>{`At ${location.pathname}${location.search}`}</h1>;
};

const renderDashboard = () =>
  render(
    <MemoryRouter initialEntries={[`/tenants/${TENANT_ID}/dashboard`]}>
      <Routes>
        <Route path="/tenants/:tenantId/dashboard" element={<Dashboard />} />
        <Route path="*" element={<Where />} />
      </Routes>
    </MemoryRouter>,
  );

/** A promise the test resolves by hand, to control response ordering. */
const deferred = <T,>() => {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
};

describe('Dashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    tenantState.current = { id: TENANT_ID, name: 'Acme', slug: 'acme', myRole: 'ADMIN' };
    dashboardGet.mockReset().mockResolvedValue(DATA);
    analyticsGet.mockReset().mockResolvedValue(ANALYTICS);
    listForTenant.mockReset().mockResolvedValue(
      page([
        task('late', 'Fuel check', { dueDate: day(-2) }),
        task('now', 'Book venue', { dueDate: day(0), priority: 'LOW' }),
        task('soon', 'Draft agenda', { dueDate: day(3) }),
        task('later', 'Plan retro', { dueDate: day(30) }),
        task('open', 'Tidy wiki', { dueDate: null }),
      ]),
    );
  });

  it.each([
    ['Active projects: 3. View active projects', `/tenants/${TENANT_ID}/projects?status=ACTIVE`],
    ['Open tasks: 12. View open tasks', `/tenants/${TENANT_ID}/tasks?focus=open`],
    ['Overdue tasks: 2. View overdue tasks', `/tenants/${TENANT_ID}/tasks?focus=overdue`],
    ['Tasks due in the next 7 days: 6. View tasks due soon', `/tenants/${TENANT_ID}/tasks?focus=due-soon`],
    ['My open tasks: 3. View my tasks', `/tenants/${TENANT_ID}/tasks?focus=mine`],
  ])('metric "%s" is a single link to %s', async (name, href) => {
    renderDashboard();
    const metric = await screen.findByRole('link', { name });
    expect(metric.getAttribute('href')).toBe(href);
    // The whole tile is the interactive element: nothing focusable nested inside it.
    expect(metric.querySelectorAll('a, button, input, select, textarea, [tabindex]')).toHaveLength(0);
  });

  it('clicking a metric opens the filtered view', async () => {
    const user = userEvent.setup();
    renderDashboard();
    await user.click(await screen.findByRole('link', { name: 'Overdue tasks: 2. View overdue tasks' }));
    expect(await screen.findByRole('heading', { name: `At /tenants/${TENANT_ID}/tasks?focus=overdue` })).toBeTruthy();
  });

  it('My work groups my open tasks by urgency, with deep links', async () => {
    renderDashboard();
    const work = (await screen.findByRole('heading', { name: 'My work' })).closest('section') as HTMLElement;
    await within(work).findByRole('link', { name: /Fuel check/ });
    expect(listForTenant).toHaveBeenCalledWith(TENANT_ID, 'mine', 0, 50, expect.any(AbortSignal));
    const groups = within(work).getAllByRole('heading', { level: 3 }).map((h) => h.textContent);
    expect(groups).toEqual(['Overdue 1', 'Due today 1', 'This week 1', 'Upcoming 1', 'No due date 1']);
    expect(within(work).getByRole('link', { name: /Fuel check/ }).getAttribute('href'))
      .toBe(`/tenants/${TENANT_ID}/projects/p1/tasks?task=late`);
    expect(within(work).getByRole('link', { name: 'View all my tasks' }).getAttribute('href')).toBe(`/tenants/${TENANT_ID}/tasks?focus=mine`);
  });

  it('flags only projects with signals and explains the criteria', async () => {
    renderDashboard();
    const health = (await screen.findByRole('heading', { name: 'Needs attention' })).closest('section') as HTMLElement;
    expect(await within(health).findByText('1 overdue task')).toBeTruthy();
    expect(within(health).getByRole('link', { name: /Apollo/ }).getAttribute('href')).toBe(`/tenants/${TENANT_ID}/projects/p1`);
    expect(within(health).queryByText(/Zephyr/)).toBeNull();
    expect(within(health).getByText('How are projects flagged?')).toBeTruthy();
  });

  it('still works when analytics fails', async () => {
    analyticsGet.mockRejectedValue({ response: { data: { message: 'Analytics offline' } } });
    renderDashboard();
    expect(await screen.findByText('Analytics offline')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Open tasks: 12. View open tasks' })).toBeTruthy();
    expect(screen.getByRole('link', { name: /Active projects: loading/ })).toBeTruthy();
  });

  it('shows team workload, with unassigned work named as such', async () => {
    renderDashboard();
    const team = (await screen.findByRole('heading', { name: 'Team workload' })).closest('section') as HTMLElement;
    expect((await within(team).findAllByText('Mia Chen')).length).toBeGreaterThan(0);
    expect(within(team).getAllByText('Unassigned').length).toBeGreaterThan(0);
  });

  it('lists recent projects with progress and owner', async () => {
    renderDashboard();
    expect(await screen.findByRole('progressbar', { name: 'Apollo progress' })).toBeTruthy();
    expect(screen.getByRole('progressbar', { name: 'Apollo progress' }).getAttribute('aria-valuenow')).toBe('25');
    const recent = screen.getByRole('heading', { name: 'Recent projects' }).closest('section') as HTMLElement;
    const row = within(recent).getByRole('link', { name: /Apollo.*1\/4 done/ });
    expect(row.getAttribute('href')).toBe(`/tenants/${TENANT_ID}/projects/p1`);
    expect(within(row).getByTitle('Ravi Patel')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'View all projects' }).getAttribute('href')).toBe(`/tenants/${TENANT_ID}/projects`);
  });

  it('shows the my-work empty state and the empty-workspace state', async () => {
    listForTenant.mockResolvedValueOnce(page([]));
    const { unmount } = renderDashboard();
    expect(await screen.findByText('Nothing assigned to you is open. Nice.')).toBeTruthy();
    unmount();

    dashboardGet.mockResolvedValueOnce({ ...DATA, totalProjects: 0, recentProjects: [], myTasks: [], upcomingTasks: [] });
    renderDashboard();
    expect(await screen.findByText('No projects yet')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Create first project' }).getAttribute('href')).toBe(`/tenants/${TENANT_ID}/projects?new=1`);
  });

  it('shows an error with a working retry', async () => {
    const user = userEvent.setup();
    dashboardGet.mockRejectedValueOnce({ response: { data: { message: 'Service unavailable' } } });
    renderDashboard();
    expect(await screen.findByText('Service unavailable')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: /Try again/ }));
    expect(await screen.findByRole('link', { name: 'Open tasks: 12. View open tasks' })).toBeTruthy();
    expect(dashboardGet).toHaveBeenCalledTimes(2);
  });

  it('switching workspace aborts the old request and never shows its late response', async () => {
    const first = deferred<DashboardData>();
    dashboardGet.mockImplementation((tenantId: string) =>
      tenantId === TENANT_ID ? first.promise : Promise.resolve({ ...DATA, todoTasks: 4, inProgressTasks: 5, currentUserRole: 'MEMBER' }),
    );
    const { rerender } = renderDashboard();
    expect(screen.getByLabelText('Loading dashboard')).toBeTruthy();
    const firstSignal = dashboardGet.mock.calls[0][1] as AbortSignal;

    tenantState.current = { id: 'tenant-b', name: 'Beta', slug: 'beta', myRole: 'MEMBER' };
    rerender(
      <MemoryRouter initialEntries={[`/tenants/${TENANT_ID}/dashboard`]}>
        <Routes>
          <Route path="/tenants/:tenantId/dashboard" element={<Dashboard />} />
        </Routes>
      </MemoryRouter>,
    );
    expect(firstSignal.aborted).toBe(true);
    const metric = await screen.findByRole('link', { name: 'Open tasks: 9. View open tasks' });
    expect(metric.getAttribute('href')).toBe('/tenants/tenant-b/tasks?focus=open');

    // The old workspace answers late: nothing changes.
    await act(async () => first.resolve(DATA));
    expect(screen.queryByRole('link', { name: /Open tasks: 12/ })).toBeNull();
    expect(screen.getByText(/^Beta · /)).toBeTruthy();
  });

  it('a failed refresh keeps the last good data visible', async () => {
    const user = userEvent.setup();
    renderDashboard();
    await screen.findByRole('link', { name: 'Open tasks: 12. View open tasks' });
    dashboardGet.mockRejectedValueOnce({ response: { data: { message: 'Temporarily unavailable' } } });
    await user.click(screen.getByRole('button', { name: 'Refresh' }));
    expect(await screen.findByText('Temporarily unavailable')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Open tasks: 12. View open tasks' })).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    await vi.waitFor(() => expect(screen.queryByText('Temporarily unavailable')).toBeNull());
  });

  it('greets the user, summarizes the workload and offers the primary actions', async () => {
    renderDashboard();
    expect(screen.getByRole('heading', { level: 1 }).textContent).toMatch(/^Good (morning|afternoon|evening), Mia$/);
    expect(await screen.findByText(/12 open tasks across 4 projects/)).toBeTruthy();
    // "New task" lives in the app header (one global action), not repeated here.
    expect(screen.queryByRole('button', { name: 'New task' })).toBeNull();
    expect(screen.getByRole('button', { name: 'New project' })).toBeTruthy();
    expect(screen.getAllByRole('link', { name: 'Analytics' })[0].getAttribute('href')).toBe(`/tenants/${TENANT_ID}/analytics`);
  });

  it('members do not get the New project action', async () => {
    tenantState.current = { id: TENANT_ID, name: 'Acme', slug: 'acme', myRole: 'MEMBER' };
    renderDashboard();
    await screen.findByRole('link', { name: 'Open tasks: 12. View open tasks' });
    expect(screen.queryByRole('button', { name: 'New project' })).toBeNull();
  });
});
