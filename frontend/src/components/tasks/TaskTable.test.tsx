import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Task } from '../../api/work';
import type { Member } from '../../pages/Members';
import { TaskTable } from './TaskTable';

const task = (id: string, title: string, overrides: Partial<Task> = {}): Task => ({
  id,
  projectId: 'p1',
  projectName: 'Apollo',
  title,
  description: null,
  status: 'TODO',
  priority: 'MEDIUM',
  assigneeId: null,
  assigneeName: null,
  createdById: null,
  createdByName: null,
  dueDate: null,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  version: 0,
  ...overrides,
});

const members: Member[] = [{ id: 'm1', userId: 'u1', name: 'Leo', email: 'leo@example.test', role: 'MEMBER', joinedAt: '' }];
const tasks = [task('a', 'Alpha'), task('b', 'Bravo'), task('c', 'Charlie', { status: 'DONE' })];

const setup = (withRemove: boolean) => {
  const bulk = { busy: false, setStatus: vi.fn(), assign: vi.fn(), remove: withRemove ? vi.fn() : undefined };
  render(<TaskTable tasks={tasks} members={members} onOpen={vi.fn()} onStatusChange={vi.fn()} bulk={bulk} />);
  return bulk;
};

describe('TaskTable bulk actions', () => {
  it('appears only after selecting rows and applies a status to exactly the selected tasks', async () => {
    const user = userEvent.setup();
    const bulk = setup(false);
    expect(screen.queryByRole('region', { name: 'Bulk actions' })).toBeNull();

    await user.click(screen.getByLabelText('Select Alpha'));
    await user.click(screen.getByLabelText('Select Charlie'));
    const bar = screen.getByRole('region', { name: 'Bulk actions' });
    expect(within(bar).getByText('2 selected')).toBeTruthy();
    expect(within(bar).queryByRole('button', { name: /Delete/ })).toBeNull(); // members cannot bulk delete

    await user.selectOptions(within(bar).getByLabelText('Set status for selected tasks'), 'IN_PROGRESS');
    expect(bulk.setStatus).toHaveBeenCalledWith(expect.arrayContaining([tasks[0], tasks[2]]), 'IN_PROGRESS');
    expect(bulk.setStatus.mock.calls[0][0]).toHaveLength(2);
    // The selection clears after the action.
    expect(screen.queryByRole('region', { name: 'Bulk actions' })).toBeNull();
  });

  it('select all, reassign, and confirmed delete for admins', async () => {
    const user = userEvent.setup();
    const bulk = setup(true);
    await user.click(screen.getByLabelText('Select all tasks'));
    const bar = screen.getByRole('region', { name: 'Bulk actions' });
    expect(within(bar).getByText('3 selected')).toBeTruthy();

    await user.selectOptions(within(bar).getByLabelText('Assign selected tasks'), 'u1');
    expect(bulk.assign).toHaveBeenCalledWith(expect.arrayContaining(tasks), 'u1');

    await user.click(screen.getByLabelText('Select Alpha'));
    await user.click(within(screen.getByRole('region', { name: 'Bulk actions' })).getByRole('button', { name: /Delete/ }));
    expect(bulk.remove).not.toHaveBeenCalled(); // confirmation first
    await user.click(await screen.findByRole('button', { name: 'Delete tasks' }));
    expect(bulk.remove).toHaveBeenCalledWith([tasks[0]]);
  });

  it('selecting a row does not open the task', async () => {
    const user = userEvent.setup();
    const onOpen = vi.fn();
    render(<TaskTable tasks={tasks} members={members} onOpen={onOpen} onStatusChange={vi.fn()} bulk={{ busy: false, setStatus: vi.fn(), assign: vi.fn() }} />);
    await user.click(screen.getByLabelText('Select Bravo'));
    expect(onOpen).not.toHaveBeenCalled();
  });
});

describe('TaskTable priority and due-date bulk actions', () => {
  it('sets priority and due date for the selected tasks, and clears due dates', async () => {
    const user = userEvent.setup();
    const bulk = { busy: false, setStatus: vi.fn(), assign: vi.fn(), setPriority: vi.fn(), setDueDate: vi.fn() };
    render(<TaskTable tasks={tasks} members={members} onOpen={vi.fn()} onStatusChange={vi.fn()} bulk={bulk} />);

    await user.click(screen.getByLabelText('Select Alpha'));
    let bar = screen.getByRole('region', { name: 'Bulk actions' });
    await user.selectOptions(within(bar).getByLabelText('Set priority for selected tasks'), 'URGENT');
    expect(bulk.setPriority).toHaveBeenCalledWith([tasks[0]], 'URGENT');

    await user.click(screen.getByLabelText('Select Bravo'));
    bar = screen.getByRole('region', { name: 'Bulk actions' });
    const set = within(bar).getByRole('button', { name: 'Set due' }) as HTMLButtonElement;
    expect(set.disabled).toBe(true);
    await user.type(within(bar).getByLabelText('Due date for selected tasks'), '2030-05-01');
    await user.click(set);
    expect(bulk.setDueDate).toHaveBeenCalledWith([tasks[1]], '2030-05-01');

    await user.click(screen.getByLabelText('Select Charlie'));
    await user.click(screen.getByRole('button', { name: 'Clear due date of selected tasks' }));
    expect(bulk.setDueDate).toHaveBeenLastCalledWith([tasks[2]], null);
  });
});

describe('TaskTable filters and saved views', () => {
  const mine = [task('a', 'Alpha', { assigneeId: 'u1' }), task('b', 'Bravo', { assigneeId: 'u2' }), task('c', 'Charlie')];

  beforeEach(() => localStorage.clear());

  it('offers "Assigned to me" only when the current user is known', async () => {
    const user = userEvent.setup();
    const { rerender } = render(<TaskTable tasks={mine} members={members} onOpen={vi.fn()} onStatusChange={vi.fn()} />);
    expect(screen.queryByRole('option', { name: 'Assigned to me' })).toBeNull();

    rerender(<TaskTable tasks={mine} members={members} onOpen={vi.fn()} onStatusChange={vi.fn()} currentUserId="u1" />);
    await user.selectOptions(screen.getByLabelText('Filter by assignee'), 'me');
    expect(screen.getByText('Alpha')).toBeTruthy();
    expect(screen.queryByText('Bravo')).toBeNull();
    expect(screen.queryByText('Charlie')).toBeNull();
  });

  it('saves the current filters as a named view, per storage key, and re-applies it', async () => {
    const user = userEvent.setup();
    const props = { tasks: mine, members, onOpen: vi.fn(), onStatusChange: vi.fn(), currentUserId: 'u1' };
    const { unmount } = render(<TaskTable {...props} viewsKey="tf.taskViews.w1" />);

    await user.selectOptions(screen.getByLabelText('Filter by assignee'), 'unassigned');
    await user.click(screen.getByRole('button', { name: 'Views' }));
    await user.type(screen.getByLabelText('View name'), 'Triage');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    unmount();

    // Fresh mount: filters reset, the saved view brings them back.
    render(<TaskTable {...props} viewsKey="tf.taskViews.w1" />);
    expect(screen.getByText('Alpha')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Views' }));
    await user.click(screen.getByRole('button', { name: 'Triage' }));
    expect(screen.queryByText('Alpha')).toBeNull();
    expect(screen.getByText('Charlie')).toBeTruthy();
    expect(JSON.parse(localStorage.getItem('tf.taskViews.w1') ?? '[]')).toHaveLength(1);
    expect(localStorage.getItem('tf.taskViews.w2')).toBeNull();
  });
});
