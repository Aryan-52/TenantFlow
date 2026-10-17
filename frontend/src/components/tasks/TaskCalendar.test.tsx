import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { Task } from '../../api/work';
import { TaskCalendar } from './TaskCalendar';

const task = (overrides: Partial<Task>): Task => ({
  id: 'x',
  projectId: 'p1',
  projectName: 'Project 1',
  title: 'Task',
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

const TASKS = [
  task({ id: 'a', title: 'Launch review', dueDate: '2030-07-17' }),
  task({ id: 'b', title: 'Retro notes', dueDate: '2031-03-09', status: 'DONE' }),
  task({ id: 'c', title: 'Someday task' }),
];

// Same formatting the component uses, so the assertions are locale-independent.
const monthTitle = (y: number, m: number) => new Date(y, m, 1).toLocaleDateString(undefined, { month: 'long', year: 'numeric' });

const renderCalendar = (onOpen = vi.fn(), onCreate = vi.fn()) => {
  render(<TaskCalendar tasks={TASKS} onOpen={onOpen} onCreate={onCreate} />);
  return { onOpen, onCreate };
};

const trigger = () => screen.getByRole('button', { name: /^Jump to date/ });

describe('TaskCalendar jump to date', () => {
  it('opens a labelled date-jump dialog from the header and focuses it', async () => {
    const user = userEvent.setup();
    renderCalendar();
    expect(trigger().getAttribute('aria-expanded')).toBe('false');
    expect(trigger().textContent).toContain(monthTitle(new Date().getFullYear(), new Date().getMonth()));

    await user.click(trigger());

    const dialog = screen.getByRole('dialog', { name: 'Jump to date' });
    expect(trigger().getAttribute('aria-expanded')).toBe('true');
    expect(document.activeElement).toBe(within(dialog).getByLabelText('Month'));
  });

  it('jumps straight to a month and year in month view', async () => {
    const user = userEvent.setup();
    renderCalendar();
    await user.click(trigger());
    const dialog = screen.getByRole('dialog', { name: 'Jump to date' });

    await user.selectOptions(within(dialog).getByLabelText('Month'), '2');
    fireEvent.change(within(dialog).getByLabelText('Year'), { target: { value: '2031' } });
    await user.click(within(dialog).getAllByRole('button', { name: 'Go' })[0]);

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(trigger().textContent).toContain(monthTitle(2031, 2));
    // The completed task due in March 2031 is now on the grid.
    expect(screen.getByRole('button', { name: 'Retro notes' }).className).toContain('due-done');
    expect(document.activeElement).toBe(trigger());
  });

  it('moves the week view to the week containing an exact date and highlights it', async () => {
    const user = userEvent.setup();
    const { container } = render(<TaskCalendar tasks={TASKS} onOpen={vi.fn()} onCreate={vi.fn()} />);
    await user.click(screen.getByRole('button', { name: 'Week' }));
    await user.click(trigger());
    const dialog = screen.getByRole('dialog', { name: 'Jump to date' });

    fireEvent.change(within(dialog).getByLabelText('Exact date'), { target: { value: '2030-07-17' } });
    await user.click(within(dialog).getAllByRole('button', { name: 'Go' })[1]);

    // 17 Jul 2030 is a Wednesday: Monday-based week is 15–21 July.
    const start = new Date(2030, 6, 15).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
    const end = new Date(2030, 6, 21).toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
    expect(trigger().textContent).toContain(`${start} – ${end}`);
    expect(screen.getByRole('button', { name: 'Launch review' })).toBeTruthy();
    const selected = container.querySelector('.cal-cell.is-selected');
    expect(selected?.textContent).toContain('17');
  });

  it('moves the day view to the chosen date and still opens tasks', async () => {
    const user = userEvent.setup();
    const { onOpen } = renderCalendar();
    await user.click(screen.getByRole('button', { name: 'Day' }));
    await user.click(trigger());
    fireEvent.change(screen.getByLabelText('Exact date'), { target: { value: '2030-07-17' } });
    await user.click(screen.getAllByRole('button', { name: 'Go' })[1]);

    const title = new Date(2030, 6, 17).toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric', year: 'numeric' });
    expect(trigger().textContent).toContain(title);
    await user.click(screen.getByRole('button', { name: /Launch review/ }));
    expect(onOpen).toHaveBeenCalledWith(expect.objectContaining({ id: 'a' }));
  });

  it('closes on Escape and returns focus to the trigger', async () => {
    const user = userEvent.setup();
    renderCalendar();
    await user.click(trigger());
    expect(screen.getByRole('dialog')).toBeTruthy();

    await user.keyboard('{Escape}');

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(document.activeElement).toBe(trigger());
  });

  it('can be operated entirely from the keyboard', async () => {
    const user = userEvent.setup();
    renderCalendar();
    trigger().focus();
    await user.keyboard('{Enter}');
    // Focus is on Month; Tab to Year, replace it, then Enter submits that row.
    await user.tab();
    expect(document.activeElement).toBe(screen.getByLabelText('Year'));
    await user.keyboard('{Control>}a{/Control}2031{Enter}');
    expect(trigger().textContent).toContain(String(2031));
  });

  it('keeps Today, previous/next and the undated-task section working', async () => {
    const user = userEvent.setup();
    renderCalendar();
    await user.click(trigger());
    fireEvent.change(screen.getByLabelText('Year'), { target: { value: '2031' } });
    await user.selectOptions(screen.getByLabelText('Month'), '2');
    await user.click(screen.getAllByRole('button', { name: 'Go' })[0]);

    await user.click(screen.getByRole('button', { name: 'Next month' }));
    expect(trigger().textContent).toContain(monthTitle(2031, 3));

    await user.click(screen.getByRole('button', { name: 'Today' }));
    expect(trigger().textContent).toContain(monthTitle(new Date().getFullYear(), new Date().getMonth()));

    expect(screen.getByRole('button', { name: 'Someday task' })).toBeTruthy();
  });

  it('rejects an invalid year instead of navigating', async () => {
    const user = userEvent.setup();
    renderCalendar();
    const before = trigger().textContent;
    await user.click(trigger());
    fireEvent.change(screen.getByLabelText('Year'), { target: { value: '12' } });
    expect((screen.getAllByRole('button', { name: 'Go' })[0] as HTMLButtonElement).disabled).toBe(true);
    await user.keyboard('{Escape}');
    expect(trigger().textContent).toBe(before);
  });
});
