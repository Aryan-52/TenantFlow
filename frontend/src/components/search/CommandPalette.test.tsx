import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CommandPalette } from './CommandPalette';
import { useGlobalShortcuts } from '../../hooks/useGlobalShortcuts';

const { search } = vi.hoisted(() => ({ search: vi.fn() }));

vi.mock('../../api/search', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/search')>();
  return { ...actual, searchApi: { search } };
});

vi.mock('../../contexts/TenantContext', () => ({
  useTenant: () => ({ currentTenant: { id: 't1', name: 'Acme', slug: 'acme', myRole: 'MEMBER' } }),
}));

const RESULT = {
  query: 'apollo',
  projects: { items: [{ id: 'p1', tenantId: 't9', tenantName: 'Beta Inc', name: 'Apollo launch', status: 'ACTIVE', description: null }], page: 0, size: 5, totalElements: 1, totalPages: 1, hasNext: false },
  tasks: { items: [{ id: 'x1', tenantId: 't9', tenantName: 'Beta Inc', projectId: 'p1', projectName: 'Apollo launch', title: 'Fuel check', status: 'TODO', priority: 'HIGH', dueDate: null }], page: 0, size: 5, totalElements: 1, totalPages: 1, hasNext: false },
};

const Where = () => {
  const location = useLocation();
  return <div data-testid="location">{location.pathname + location.search}</div>;
};

/** Mirrors how AppLayout wires the palette and shortcuts. */
const Harness = () => {
  const [open, setOpen] = useState(false);
  useGlobalShortcuts({ onOpenPalette: () => setOpen(true), onGoTo: () => undefined });
  return (
    <>
      <input aria-label="Some field" />
      {open && <CommandPalette onClose={() => setOpen(false)} />}
      <Where />
    </>
  );
};

const renderAt = (ui: React.ReactNode) =>
  render(
    <MemoryRouter initialEntries={['/start']}>
      <Routes>
        <Route path="*" element={ui} />
      </Routes>
    </MemoryRouter>,
  );

describe('CommandPalette', () => {
  beforeEach(() => {
    search.mockReset().mockResolvedValue(RESULT);
  });

  it('shows navigation commands immediately and grouped search results after typing', async () => {
    const user = userEvent.setup();
    renderAt(<CommandPalette onClose={vi.fn()} />);
    expect(screen.getByRole('option', { name: /Dashboard/ })).toBeTruthy();
    expect(screen.queryByRole('option', { name: /Workspace settings/ })).toBeNull(); // members only

    await user.type(screen.getByRole('combobox'), 'apollo');
    expect(await screen.findByRole('option', { name: /Fuel check/ })).toBeTruthy();
    expect(search).toHaveBeenLastCalledWith('apollo', expect.any(AbortSignal));
    expect(screen.getByRole('group', { name: 'Projects' }).textContent).toContain('Apollo launch');
    expect(screen.getByRole('group', { name: 'Tasks' }).textContent).toContain('Apollo launch · Beta Inc');
  });

  it('is fully keyboard operable and opens the task in its own workspace', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderAt(<><CommandPalette onClose={onClose} /><Where /></>);
    const input = screen.getByRole('combobox');
    await user.type(input, 'apollo');
    await screen.findByRole('option', { name: /Fuel check/ });

    // Options: Projects/Apollo launch, Tasks/Fuel check (no command matches "apollo").
    await user.keyboard('{ArrowDown}');
    const active = input.getAttribute('aria-activedescendant');
    expect(active).toBe('palette-task-x1');
    expect(screen.getByRole('option', { name: /Fuel check/ }).getAttribute('aria-selected')).toBe('true');
    await user.keyboard('{Enter}');

    expect(onClose).toHaveBeenCalled();
    expect(screen.getByTestId('location').textContent).toBe('/tenants/t9/projects/p1/tasks?task=x1');
  });

  it('closes on Escape and explains short queries and rate limiting', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    renderAt(<CommandPalette onClose={onClose} />);
    await user.type(screen.getByRole('combobox'), 'a');
    expect(screen.getByText('Type at least 2 characters to search.')).toBeTruthy();
    expect(search).not.toHaveBeenCalled();

    search.mockRejectedValueOnce({ response: { status: 429 } });
    await user.type(screen.getByRole('combobox'), 'b');
    expect(await screen.findByText('Searching too quickly. Wait a moment and try again.')).toBeTruthy();

    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalled();
  });

  it('opens with Ctrl+K or "/", but "/" and "g" shortcuts are ignored while typing in a field', async () => {
    const user = userEvent.setup();
    renderAt(<Harness />);

    await user.click(screen.getByLabelText('Some field'));
    await user.keyboard('/');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect((screen.getByLabelText('Some field') as HTMLInputElement).value).toBe('/');

    await user.keyboard('{Control>}k{/Control}');
    expect(await screen.findByRole('dialog', { name: 'Search and commands' })).toBeTruthy();
    await user.keyboard('{Escape}');
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());

    (document.activeElement as HTMLElement | null)?.blur();
    await user.keyboard('/');
    expect(await screen.findByRole('dialog')).toBeTruthy();
  });
});

describe('CommandPalette people, files, actions and recents', () => {
  beforeEach(() => {
    localStorage.clear();
    search.mockReset().mockResolvedValue({
      ...RESULT,
      members: { items: [{ userId: 'u7', tenantId: 't9', tenantName: 'Beta Inc', name: 'Apollo Reyes', email: 'apollo@example.test', role: 'MEMBER' }], page: 0, size: 5, totalElements: 1, totalPages: 1, hasNext: false },
      files: { items: [{ id: 'f1', tenantId: 't9', tenantName: 'Beta Inc', projectId: 'p1', projectName: 'Apollo launch', taskId: null, filename: 'apollo-brief.pdf', contentType: 'application/pdf', sizeBytes: 10 }], page: 0, size: 5, totalElements: 1, totalPages: 1, hasNext: false },
    });
  });

  it('groups people and files and opens them in their workspace', async () => {
    const user = userEvent.setup();
    renderAt(<><CommandPalette onClose={vi.fn()} /><Where /></>);
    await user.type(screen.getByRole('combobox'), 'apollo');
    expect(await screen.findByRole('option', { name: /Apollo Reyes/ })).toBeTruthy();
    expect(screen.getByRole('group', { name: 'People' }).textContent).toContain('apollo@example.test');
    await user.click(screen.getByRole('option', { name: /apollo-brief\.pdf/ }));
    expect(screen.getByTestId('location').textContent).toBe('/tenants/t9/projects/p1/files');
  });

  it('remembers opened results as recent destinations', async () => {
    const user = userEvent.setup();
    const { unmount } = renderAt(<CommandPalette onClose={vi.fn()} />);
    expect(screen.queryByRole('group', { name: 'Recent' })).toBeNull();
    await user.type(screen.getByRole('combobox'), 'apollo');
    await user.click(await screen.findByRole('option', { name: /Fuel check/ }));
    unmount();

    renderAt(<CommandPalette onClose={vi.fn()} />);
    expect(screen.getByRole('group', { name: 'Recent' }).textContent).toContain('Fuel check');
  });

  it('runs the New task action through the host', async () => {
    const user = userEvent.setup();
    const onCreateTask = vi.fn();
    const onClose = vi.fn();
    renderAt(<CommandPalette onClose={onClose} onCreateTask={onCreateTask} />);
    await user.click(screen.getByRole('option', { name: /New task/ }));
    expect(onClose).toHaveBeenCalled();
    expect(onCreateTask).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('option', { name: /New project/ })).toBeNull(); // members cannot create projects
  });
});
