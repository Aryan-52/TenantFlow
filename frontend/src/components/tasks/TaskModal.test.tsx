import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { Task } from '../../api/work';
import { TaskModal } from './TaskModal';

const { create, update } = vi.hoisted(() => ({ create: vi.fn(), update: vi.fn() }));
vi.mock('../../api/work', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/work')>();
  return { ...actual, taskApi: { ...actual.taskApi, create, update } };
});
// The drawer's side panels load their own data; keep them quiet here.
vi.mock('./TaskDependencies', () => ({ TaskDependencies: () => null }));
vi.mock('./TaskComments', () => ({ TaskComments: () => null }));

const renderCreate = (onClose = vi.fn()) => {
  render(
    <TaskModal
      isOpen
      onClose={onClose}
      tenantId="t1"
      projectId="p1"
      task={null}
      members={[]}
      canDelete={false}
      onSaved={vi.fn()}
      onDeleted={vi.fn()}
    />,
  );
  return onClose;
};

describe('TaskModal unsaved changes', () => {
  it('closes straight away when nothing was typed', async () => {
    const user = userEvent.setup();
    const onClose = renderCreate();
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('asks before discarding typed input, and can keep editing', async () => {
    const user = userEvent.setup();
    const onClose = renderCreate();
    await user.type(screen.getByLabelText(/Title/), 'Draft launch plan');

    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.getByText('Discard changes?')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Keep editing' }));
    expect((screen.getByLabelText(/Title/) as HTMLInputElement).value).toBe('Draft launch plan');

    await user.keyboard('{Escape}');
    await user.click(screen.getByRole('button', { name: 'Discard' }));
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});

const existing: Task = {
  id: 't1', projectId: 'p1', projectName: 'Apollo', title: 'Weekly sync notes', description: null, status: 'TODO',
  priority: 'MEDIUM', assigneeId: null, assigneeName: null, createdById: null, createdByName: null, dueDate: '2030-03-04',
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', version: 3, startDate: '2030-03-01',
  milestoneId: 'm1', recurrence: null, openBlockers: 0,
};
const milestones = [
  { id: 'm1', projectId: 'p1', projectName: 'Apollo', name: 'Beta', description: null, targetDate: '2030-03-10', status: 'OPEN' as const, completedAt: null, totalTasks: 1, doneTasks: 0, version: 0 },
];

describe('TaskModal planning fields', () => {
  it('sends start date, milestone and a weekly repeat rule together', async () => {
    const user = userEvent.setup();
    update.mockResolvedValue(existing);
    render(
      <TaskModal isOpen onClose={vi.fn()} tenantId="ten" projectId="p1" task={existing} members={[]} milestones={milestones}
        canDelete={false} onSaved={vi.fn()} onDeleted={vi.fn()} />,
    );
    expect((screen.getByLabelText('Start date') as HTMLInputElement).value).toBe('2030-03-01');
    expect((screen.getByLabelText('Milestone') as HTMLSelectElement).value).toBe('m1');
    await user.selectOptions(screen.getByLabelText('Repeat'), 'WEEKLY');
    await user.click(screen.getByRole('button', { name: 'Monday' }));
    await user.click(screen.getByRole('button', { name: 'Thursday' }));
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(update).toHaveBeenCalledWith('ten', 'p1', 't1', expect.objectContaining({
      version: 3,
      planning: { startDate: '2030-03-01', milestoneId: 'm1', recurrence: { frequency: 'WEEKLY', interval: 1, weekdays: 0b1001, until: null } },
    }));
  });

  it('checks dates before sending: due on or after start, and repeating tasks need a due date', async () => {
    const user = userEvent.setup();
    update.mockReset();
    render(
      <TaskModal isOpen onClose={vi.fn()} tenantId="ten" projectId="p1" task={existing} members={[]} milestones={milestones}
        canDelete={false} onSaved={vi.fn()} onDeleted={vi.fn()} />,
    );
    await user.clear(screen.getByLabelText('Start date'));
    await user.type(screen.getByLabelText('Start date'), '2030-03-09');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    expect(screen.getByText('The due date must be on or after the start date')).toBeTruthy();
    expect(update).not.toHaveBeenCalled();
  });

  it('a new task can be created with planning fields from the folded section', async () => {
    const user = userEvent.setup();
    create.mockResolvedValue({ ...existing, id: 'new' });
    render(<TaskModal isOpen onClose={vi.fn()} tenantId="ten" projectId="p1" task={null} members={[]} canDelete={false} onSaved={vi.fn()} onDeleted={vi.fn()} />);
    await user.type(screen.getByLabelText(/Title/), 'Pay invoices');
    await user.click(screen.getByText('Planning: start date, milestone, repeat'));
    await user.selectOptions(screen.getByLabelText('Repeat'), 'MONTHLY');
    await user.click(screen.getByRole('button', { name: 'Create task' }));
    expect(screen.getByText('A repeating task needs a due date')).toBeTruthy();
    expect(create).not.toHaveBeenCalled();
    await user.type(screen.getByLabelText('Due date'), '2030-04-30');
    await user.click(screen.getByRole('button', { name: 'Create task' }));
    expect(create).toHaveBeenCalledWith('ten', 'p1', expect.objectContaining({
      title: 'Pay invoices',
      dueDate: '2030-04-30',
      planning: { startDate: null, milestoneId: null, recurrence: { frequency: 'MONTHLY', interval: 1, weekdays: 0, until: null } },
    }), expect.any(String));
  });
});
