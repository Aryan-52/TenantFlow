import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Task } from '../api/work';
import type { MemberLoad, MyWork as MyWorkData, PortfolioRow, Workload } from '../api/planning';
import { addDays, toDateKey } from '../components/tasks/taskMeta';
import MyWork from './MyWork';
import Portfolio from './Portfolio';
import Planning from './Planning';

const { myWorkGet, focus, unfocus, portfolio, workload, setCapacity, updateStatus, tenantState } = vi.hoisted(() => ({
  myWorkGet: vi.fn(),
  focus: vi.fn(),
  unfocus: vi.fn(),
  portfolio: vi.fn(),
  workload: vi.fn(),
  setCapacity: vi.fn(),
  updateStatus: vi.fn(),
  tenantState: { current: { id: 't1', name: 'Acme', slug: 'acme', myRole: 'MEMBER' } },
}));

vi.mock('../api/planning', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/planning')>()),
  myWorkApi: { get: myWorkGet, focus, unfocus },
  planningApi: { portfolio, workload, setCapacity },
}));
vi.mock('../api/work', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/work')>();
  return { ...actual, taskApi: { ...actual.taskApi, updateStatus }, projectApi: { ...actual.projectApi, list: vi.fn(() => Promise.resolve([])) } };
});
vi.mock('../api/client', () => ({ default: { get: vi.fn(() => Promise.resolve({ data: [] })) } }));
vi.mock('../contexts/TenantContext', () => ({ useTenant: () => ({ currentTenant: tenantState.current }) }));
vi.mock('../contexts/AuthContext', () => ({ useAuth: () => ({ user: { id: 'me', name: 'Mia' } }) }));

const day = (n: number) => toDateKey(addDays(new Date(), n));
const task = (id: string, title: string, o: Partial<Task> = {}): Task => ({
  id, projectId: 'p1', projectName: 'Apollo', title, description: null, status: 'TODO', priority: 'MEDIUM',
  assigneeId: 'me', assigneeName: 'Mia', createdById: null, createdByName: null, dueDate: null,
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', version: 0, openBlockers: 0, ...o,
});

const renderPage = (ui: React.ReactNode) => render(<MemoryRouter>{ui}</MemoryRouter>);

describe('My Work', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    const data: MyWorkData = {
      today: day(0),
      open: [
        task('a', 'Late report', { dueDate: day(-2), priority: 'HIGH' }),
        task('b', 'Pinned review', { dueDate: day(20) }),
        task('c', 'Waiting on design', { dueDate: day(1), openBlockers: 2 }),
        task('d', 'Someday', { dueDate: null }),
      ],
      focusTaskIds: ['b'],
      completed: [task('z', 'Shipped thing', { status: 'DONE', completedAt: new Date().toISOString() })],
      completedDays: 14,
    };
    myWorkGet.mockResolvedValue(data);
    focus.mockResolvedValue({});
    unfocus.mockResolvedValue({});
  });

  it('groups real tasks by urgency, today pins and blockers, with a focus list', async () => {
    renderPage(<MyWork />);
    const overdue = (await screen.findByRole('heading', { name: /Overdue/ })).closest('section') as HTMLElement;
    expect(within(overdue).getByRole('link', { name: 'Late report' })).toBeTruthy();
    const today = screen.getByRole('heading', { name: /^Today/ }).closest('section') as HTMLElement;
    expect(within(today).getByRole('link', { name: 'Pinned review' })).toBeTruthy();
    const blocked = screen.getByRole('heading', { name: /Waiting and blocked/ }).closest('section') as HTMLElement;
    expect(within(blocked).getByText(/Blocked by 2/)).toBeTruthy();
    expect(screen.getByRole('heading', { name: /No due date/ })).toBeTruthy();
    // Focus: unblocked and due/pinned, most important first. Blocked work is not suggested.
    const focusList = screen.getByRole('heading', { name: 'Focus' }).closest('section') as HTMLElement;
    expect(within(focusList).getAllByRole('link').map((l) => l.textContent)).toEqual([
      expect.stringContaining('Late report'),
      expect.stringContaining('Pinned review'),
    ]);
    expect(screen.getByRole('link', { name: 'Shipped thing' })).toBeTruthy();
  });

  it('pins and unpins tasks for today', async () => {
    const user = userEvent.setup();
    renderPage(<MyWork />);
    await user.click(await screen.findByRole('button', { name: 'Add Late report to today' }));
    expect(focus).toHaveBeenCalledWith('t1', 'a');
    await user.click(screen.getByRole('button', { name: 'Remove Pinned review from today' }));
    expect(unfocus).toHaveBeenCalledWith('t1', 'b');
  });

  it('completing a task inline moves it to completed', async () => {
    const user = userEvent.setup();
    updateStatus.mockResolvedValue(task('d', 'Someday', { status: 'DONE' }));
    renderPage(<MyWork />);
    await user.selectOptions(await screen.findByLabelText('Status of Someday'), 'DONE');
    expect(updateStatus).toHaveBeenCalledWith('t1', 'p1', 'd', 'DONE');
    expect(screen.queryByLabelText('Status of Someday')).toBeNull();
  });

  it('filters by search and shows an empty state', async () => {
    const user = userEvent.setup();
    renderPage(<MyWork />);
    await user.type(await screen.findByLabelText('Search my tasks'), 'nothing like this');
    expect(screen.getByText('No task matches these filters')).toBeTruthy();
  });
});

const row = (o: Partial<PortfolioRow>): PortfolioRow => ({
  id: 'p', name: 'P', description: null, status: 'ACTIVE', ownerId: null, ownerName: null, startDate: null, targetDate: null,
  totalTasks: 4, doneTasks: 1, openTasks: 3, overdueTasks: 0, blockedTasks: 0, milestonesOpen: 0, milestonesDone: 0,
  nextMilestone: null, lastActivityAt: new Date().toISOString(), signals: [], ...o,
});

describe('Portfolio', () => {
  beforeEach(() => {
    localStorage.clear();
    portfolio.mockResolvedValue([
      row({ id: 'a', name: 'Apollo', ownerId: 'u1', ownerName: 'Ada', signals: [{ code: 'TARGET_PASSED', severity: 'high', message: 'Target date passed 2 days ago with 3 open tasks' }] }),
      row({ id: 'b', name: 'Beta', status: 'COMPLETED' }),
      row({ id: 'c', name: 'Cosmos', status: 'ON_HOLD' }),
    ]);
  });

  it('lists active and on-hold projects with explained warnings, and filters by owner', async () => {
    const user = userEvent.setup();
    renderPage(<Portfolio />);
    expect(await screen.findByRole('link', { name: /Apollo/ })).toBeTruthy();
    expect(screen.getByText('Target date passed 2 days ago with 3 open tasks')).toBeTruthy();
    expect(screen.queryByRole('link', { name: /Beta/ })).toBeNull(); // completed is hidden by default
    await user.selectOptions(screen.getByLabelText('Status'), '');
    expect(screen.getByRole('link', { name: /Beta/ })).toBeTruthy();
    await user.selectOptions(screen.getByLabelText('Owner'), 'u1');
    expect(screen.queryByRole('link', { name: /Cosmos/ })).toBeNull();
  });

  it('switches to cards and remembers the choice', async () => {
    const user = userEvent.setup();
    renderPage(<Portfolio />);
    await screen.findByRole('link', { name: /Apollo/ });
    await user.click(screen.getByRole('button', { name: /Cards/ }));
    expect(screen.getByRole('progressbar', { name: 'Apollo progress' })).toBeTruthy();
    expect(JSON.parse(localStorage.getItem('tf.portfolio')!).view).toBe('cards');
  });
});

const load = (o: Partial<MemberLoad>): MemberLoad => ({
  userId: 'u', name: 'U', email: null, role: 'MEMBER', weeklyCapacity: null, open: 0, todo: 0, inProgress: 0, overdue: 0,
  blocked: 0, noDueDate: 0, weeks: [{ weekStart: day(0), due: 0 }], later: 0, projects: [], warning: null, ...o,
});

describe('Planning', () => {
  beforeEach(() => {
    const data: Workload = {
      today: day(0),
      weeks: 1,
      members: [
        load({ userId: 'me', name: 'Mia', open: 5, overdue: 2, weeklyCapacity: 3, warning: '5 tasks due this week or overdue, above the capacity of 3 per week' }),
        load({ userId: 'u2', name: 'Raj', open: 1 }),
      ],
      unassigned: load({ userId: null, name: null, role: null, open: 2 }),
    };
    workload.mockResolvedValue(data);
    setCapacity.mockResolvedValue({ ...data, weeks: 6 });
  });

  it('shows workload with explicit capacity units and lets members edit only their own', async () => {
    renderPage(<Planning />);
    expect(await screen.findByText(/above the capacity of 3 per week/)).toBeTruthy();
    expect(screen.getByLabelText('Weekly capacity of Mia in tasks')).toBeTruthy();
    expect(screen.queryByLabelText('Weekly capacity of Raj in tasks')).toBeNull(); // members cannot edit others
    expect(screen.getByRole('link', { name: 'Unassigned' })).toBeTruthy();
    expect(screen.getByText(/not a measure of anyone's productivity/)).toBeTruthy();
  });

  it('saves a capacity', async () => {
    const user = userEvent.setup();
    renderPage(<Planning />);
    const input = await screen.findByLabelText('Weekly capacity of Mia in tasks');
    await user.clear(input);
    await user.type(input, '6');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(setCapacity).toHaveBeenCalledWith('t1', 'me', 6);
  });
});
