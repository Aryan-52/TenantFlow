import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Task } from '../api/work';
import { addDays, toDateKey } from '../components/tasks/taskMeta';
import CalendarPage from './CalendarPage';
import Documents from './Documents';
import Reports from './Reports';
import Automations from './Automations';

const { calendar, docs, report, csv, rules, runs, toggle, saveBlob, tenantState } = vi.hoisted(() => ({
  calendar: vi.fn(),
  docs: vi.fn(),
  report: vi.fn(),
  csv: vi.fn(),
  rules: vi.fn(),
  runs: vi.fn(),
  toggle: vi.fn(),
  saveBlob: vi.fn(),
  tenantState: { current: { id: 't1', name: 'Acme', slug: 'acme', myRole: 'ADMIN' } },
}));

vi.mock('../api/planning', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/planning')>()),
  planningApi: { calendar },
}));
vi.mock('../api/workspace', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/workspace')>()),
  documentApi: { list: docs },
  reportApi: { get: report, csv },
  automationApi: { rules, runs, toggle },
  saveBlob,
}));
vi.mock('../api/work', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/work')>();
  return { ...actual, projectApi: { ...actual.projectApi, list: vi.fn(() => Promise.resolve([{ id: 'p1', name: 'Apollo' }])) } };
});
vi.mock('../api/client', () => ({ default: { get: vi.fn(() => Promise.resolve({ data: [{ userId: 'u1', name: 'Ada' }] })) } }));
vi.mock('../contexts/TenantContext', () => ({ useTenant: () => ({ currentTenant: tenantState.current }) }));
vi.mock('../contexts/AuthContext', () => ({ useAuth: () => ({ user: { id: 'me', name: 'Mia' } }) }));

const page = <T,>(items: T[]) => ({ items, page: 0, size: 25, totalElements: items.length, totalPages: 1, hasNext: false });
const today = toDateKey(new Date());
const task = (id: string, title: string, o: Partial<Task> = {}): Task => ({
  id, projectId: 'p1', projectName: 'Apollo', title, description: null, status: 'TODO', priority: 'MEDIUM',
  assigneeId: 'me', assigneeName: 'Mia', createdById: null, createdByName: null, dueDate: today,
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', version: 0, ...o,
});
const renderPage = (ui: React.ReactNode) => render(<MemoryRouter>{ui}</MemoryRouter>);

describe('Calendar', () => {
  beforeEach(() => {
    calendar.mockReset().mockImplementation((_t: string, p: { scope: string }) =>
      Promise.resolve({
        from: today, to: today, scope: p.scope, truncated: false,
        tasks: p.scope === 'mine' ? [task('a', 'My report')] : [task('a', 'My report'), task('b', 'Team review', { assigneeName: 'Raj' })],
        milestones: [{ id: 'm1', projectId: 'p1', projectName: 'Apollo', name: 'Launch', description: null, targetDate: today, status: 'OPEN', completedAt: null, totalTasks: 2, doneTasks: 1, version: 0 }],
      }),
    );
  });

  it("separates my schedule from the workspace's and shows the day agenda", async () => {
    const user = userEvent.setup();
    renderPage(<CalendarPage />);
    const agenda = (await screen.findByRole('complementary', { name: /./ })) as HTMLElement;
    expect(await within(agenda).findByRole('link', { name: 'My report' })).toBeTruthy();
    expect(within(agenda).getByRole('link', { name: 'Launch' })).toBeTruthy();
    expect(calendar).toHaveBeenLastCalledWith('t1', expect.objectContaining({ scope: 'mine' }), expect.any(AbortSignal));
    await user.click(screen.getByRole('button', { name: 'Workspace' }));
    expect(await within(agenda).findByRole('link', { name: 'Team review' })).toBeTruthy();
  });

  it('moves by month and week and asks for the visible range only', async () => {
    const user = userEvent.setup();
    renderPage(<CalendarPage />);
    await screen.findAllByRole('link', { name: 'My report' });
    await user.click(screen.getByRole('button', { name: 'Week' }));
    const last = calendar.mock.calls.at(-1)![1] as { from: string; to: string };
    expect(toDateKey(addDays(new Date(last.from + 'T00:00:00'), 6))).toBe(last.to);
    await user.click(screen.getByRole('button', { name: 'Next week' }));
    expect((calendar.mock.calls.at(-1)![1] as { from: string }).from > last.from).toBe(true);
  });
});

describe('Documents', () => {
  beforeEach(() => {
    docs.mockReset().mockResolvedValue(page([
      { id: 'd1', projectId: 'p1', projectName: 'Apollo', taskId: 't9', taskTitle: 'Spec', filename: 'plan.pdf', contentType: 'application/pdf', sizeBytes: 2048, uploadedById: 'u1', uploadedByName: 'Ada', createdAt: new Date().toISOString() },
      { id: 'd2', projectId: 'p1', projectName: 'Apollo', taskId: null, taskTitle: null, filename: 'mine.png', contentType: 'image/png', sizeBytes: 10, uploadedById: 'me', uploadedByName: 'Mia', createdAt: new Date().toISOString() },
    ]));
  });

  it('lists files with their location and sends filters to the server', async () => {
    const user = userEvent.setup();
    tenantState.current = { ...tenantState.current, myRole: 'MEMBER' };
    renderPage(<Documents />);
    expect(await screen.findByText('plan.pdf')).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Task: Spec' })).toBeTruthy();
    // Members can delete only their own uploads.
    expect(screen.queryByRole('button', { name: 'Delete plan.pdf' })).toBeNull();
    expect(screen.getByRole('button', { name: 'Delete mine.png' })).toBeTruthy();
    await user.selectOptions(screen.getByLabelText('File type'), 'pdf');
    expect(docs).toHaveBeenLastCalledWith('t1', expect.objectContaining({ type: 'pdf', page: 0 }), expect.any(AbortSignal));
    tenantState.current = { ...tenantState.current, myRole: 'ADMIN' };
  });
});

describe('Reports', () => {
  beforeEach(() => {
    report.mockReset().mockResolvedValue({
      type: 'overdue-tasks', title: 'Overdue tasks', description: 'Open tasks past their due date.', filters: {}, generatedAt: new Date().toISOString(),
      columns: [{ key: 'task', label: 'Task', numeric: false }, { key: 'daysOverdue', label: 'Days overdue', numeric: true }],
      rows: [['Fix login', '4']],
    });
    csv.mockReset().mockResolvedValue({ blob: new Blob(['x']), filename: 'tenantflow-overdue-tasks.csv' });
  });

  it('shows live rows and exports the same filters as CSV', async () => {
    const user = userEvent.setup();
    renderPage(<Reports />);
    await user.click(await screen.findByRole('tab', { name: /Overdue tasks/ }));
    expect(await screen.findByRole('cell', { name: 'Fix login' })).toBeTruthy();
    await user.selectOptions(await screen.findByLabelText('Assignee'), 'u1');
    await user.click(screen.getByRole('button', { name: /Export CSV/ }));
    expect(csv).toHaveBeenCalledWith('t1', 'overdue-tasks', expect.objectContaining({ assigneeId: 'u1' }));
    expect(saveBlob).toHaveBeenCalledWith(expect.any(Blob), 'tenantflow-overdue-tasks.csv');
  });
});

describe('Automations', () => {
  const rule = (enabled: boolean) => ({ type: 'URGENT_NOTIFY_OWNER', trigger: 'A task is created as Urgent', action: 'Notify the project owner', enabled, updatedAt: null, runs: 2, failures: 1, lastRunAt: new Date().toISOString() });
  beforeEach(() => {
    rules.mockReset().mockResolvedValue([rule(false)]);
    runs.mockReset().mockResolvedValue(page([{ id: 'r1', type: 'URGENT_NOTIFY_OWNER', taskId: null, outcome: 'SKIPPED', message: 'The project has no owner', createdAt: new Date().toISOString() }]));
    toggle.mockReset().mockResolvedValue([rule(true)]);
  });

  it('admins switch rules on; history explains skipped runs', async () => {
    const user = userEvent.setup();
    renderPage(<Automations />);
    const sw = await screen.findByRole('switch', { name: /Flag urgent work: off/ });
    expect(screen.getByText('The project has no owner')).toBeTruthy();
    expect(screen.getByText(/1 failed/)).toBeTruthy();
    await user.click(sw);
    expect(toggle).toHaveBeenCalledWith('t1', 'URGENT_NOTIFY_OWNER', true);
    expect(await screen.findByRole('switch', { name: /Flag urgent work: on/ })).toBeTruthy();
  });

  it('members see rules but cannot change them', async () => {
    tenantState.current = { ...tenantState.current, myRole: 'MEMBER' };
    renderPage(<Automations />);
    expect(((await screen.findByRole('switch')) as HTMLInputElement).disabled).toBe(true);
    tenantState.current = { ...tenantState.current, myRole: 'ADMIN' };
  });
});
