import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Project, Task } from '../api/work';
import Tasks from './Tasks';

const TENANT_ID = 't1';

const { list, listProjects, listForTenant } = vi.hoisted(() => ({ list: vi.fn(), listProjects: vi.fn(), listForTenant: vi.fn() }));

vi.mock('../api/work', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/work')>();
  return {
    ...actual,
    projectApi: { ...actual.projectApi, list: listProjects },
    taskApi: { ...actual.taskApi, list, listForTenant },
  };
});

vi.mock('../api/client', () => ({
  default: { get: vi.fn(() => Promise.resolve({ data: [] })) },
}));

vi.mock('../contexts/TenantContext', () => ({
  useTenant: () => ({ currentTenant: { id: TENANT_ID, name: 'Acme', slug: 'acme', myRole: 'ADMIN' } }),
}));

vi.mock('../contexts/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'u1', name: 'Me', email: 'me@example.test' } }),
}));

const project = (id: string, name: string) => ({ id, name }) as Project;
const task = (id: string, title: string, projectId: string) =>
  ({
    id,
    title,
    projectId,
    projectName: projectId === 'p1' ? 'Alpha' : 'Beta',
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
  }) as Task;

const renderTasks = () =>
  render(
    <MemoryRouter initialEntries={[`/tenants/${TENANT_ID}/tasks`]}>
      <Routes>
        <Route path="/tenants/:tenantId/tasks" element={<Tasks />} />
      </Routes>
    </MemoryRouter>,
  );

describe('Tasks page toolbar', () => {
  beforeEach(() => {
    listProjects.mockReset().mockResolvedValue([project('p1', 'Alpha'), project('p2', 'Beta')]);
    list.mockReset().mockImplementation((_t: string, projectId: string) =>
      Promise.resolve(projectId === 'p1' ? [task('a', 'Alpha task', 'p1'), task('b', 'Write docs', 'p1')] : [task('c', 'Beta task', 'p2')]),
    );
  });

  it('puts project selector, search, filters and Create task in one toolbar', async () => {
    renderTasks();
    await screen.findByText('Alpha task');

    const toolbar = screen.getByLabelText('Project').closest('.task-toolbar') as HTMLElement;
    expect(toolbar).toBeTruthy();
    expect(toolbar.classList.contains('has-project')).toBe(true);
    const inToolbar = within(toolbar);
    expect(inToolbar.getByPlaceholderText('Search tasks...')).toBeTruthy();
    for (const label of ['Filter by status', 'Filter by priority', 'Filter by assignee', 'Filter by due date', 'Sort tasks']) {
      expect(inToolbar.getByLabelText(label)).toBeTruthy();
    }
    expect(inToolbar.getByRole('button', { name: /Create task/ })).toBeTruthy();
    // Nothing duplicated in the page header any more.
    expect(screen.getAllByRole('button', { name: /Create task/ })).toHaveLength(1);
  });

  it('keeps search and project scoping working', async () => {
    const user = userEvent.setup();
    renderTasks();
    await screen.findByText('Alpha task');

    await user.type(screen.getByPlaceholderText('Search tasks...'), 'docs');
    expect(screen.queryByText('Alpha task')).toBeNull();
    expect(screen.getByText('Write docs')).toBeTruthy();

    await user.selectOptions(screen.getByLabelText('Project'), 'p2');
    await waitFor(() => expect(list).toHaveBeenLastCalledWith(TENANT_ID, 'p2'));
    // The toolbar (and its project selector) stays mounted while the new project loads.
    expect(screen.getByLabelText('Project')).toBeTruthy();
    await waitFor(() => expect(screen.queryByText('Alpha task')).toBeNull());
  });

  it('Create task opens the task form for the selected project', async () => {
    const user = userEvent.setup();
    renderTasks();
    await screen.findByText('Alpha task');
    await user.click(screen.getByRole('button', { name: /Create task/ }));
    expect(await screen.findByRole('dialog', { name: 'Create task' })).toBeTruthy();
  });

  it('shows an empty state instead of the toolbar when the workspace has no projects', async () => {
    listProjects.mockResolvedValue([]);
    renderTasks();
    expect(await screen.findByText('No projects yet')).toBeTruthy();
    expect(screen.queryByLabelText('Project')).toBeNull();
  });
});

const Where = () => {
  const location = useLocation();
  return <div data-testid="location">{location.pathname + location.search}</div>;
};

const renderFocus = (focus: string) =>
  render(
    <MemoryRouter initialEntries={[`/tenants/${TENANT_ID}/tasks?focus=${focus}`]}>
      <Routes>
        <Route path="/tenants/:tenantId/tasks" element={<><Tasks /><Where /></>} />
      </Routes>
    </MemoryRouter>,
  );

const page = (items: Task[], pageNo: number, total: number, hasNext: boolean) =>
  ({ items, page: pageNo, size: 50, totalElements: total, totalPages: 2, hasNext });

describe('Tasks page focus views (from the dashboard)', () => {
  beforeEach(() => {
    listProjects.mockReset().mockResolvedValue([project('p1', 'Alpha'), project('p2', 'Beta')]);
    list.mockReset();
    listForTenant.mockReset().mockImplementation((_t: string, _f: string, pageNo: number) =>
      Promise.resolve(pageNo === 0
        ? page([task('a', 'Alpha task', 'p1'), task('c', 'Beta task', 'p2')], 0, 3, true)
        : page([task('d', 'Late page task', 'p2')], 1, 3, false)),
    );
  });

  it('lists tasks across projects for the focus, with project names and paging', async () => {
    const user = userEvent.setup();
    renderFocus('overdue');
    expect(await screen.findByRole('heading', { name: 'Overdue tasks' })).toBeTruthy();
    await screen.findByText('Beta task');
    expect(listForTenant).toHaveBeenCalledWith(TENANT_ID, 'overdue', 0, 50, expect.any(AbortSignal));
    expect(list).not.toHaveBeenCalled();
    expect(screen.getAllByText('Beta').length).toBeGreaterThan(0); // project shown per row
    expect(screen.getByText('3 across all projects in this workspace.')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Load more (2 of 3)' }));
    expect(await screen.findByText('Late page task')).toBeTruthy();
    expect(screen.getByText('Alpha task')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Load more/ })).toBeNull();
  });

  it('switches between focus views and back to the per-project list', async () => {
    const user = userEvent.setup();
    list.mockResolvedValue([task('a', 'Alpha task', 'p1')]);
    renderFocus('mine');
    await screen.findByText('Beta task');
    await user.selectOptions(screen.getByLabelText('Task view'), 'due-soon');
    expect(screen.getByTestId('location').textContent).toBe(`/tenants/${TENANT_ID}/tasks?focus=due-soon`);
    expect(await screen.findByRole('heading', { name: 'Due in the next 7 days' })).toBeTruthy();

    await user.selectOptions(screen.getByLabelText('Task view'), '');
    expect(await screen.findByLabelText('Project')).toBeTruthy();
    await waitFor(() => expect(list).toHaveBeenCalledWith(TENANT_ID, 'p1'));
  });

  it('an unknown focus falls back to the per-project list', async () => {
    list.mockResolvedValue([]);
    renderFocus('everything');
    expect(await screen.findByLabelText('Project')).toBeTruthy();
    expect(listForTenant).not.toHaveBeenCalled();
  });

  it('shows a load error with a working retry', async () => {
    const user = userEvent.setup();
    listForTenant.mockRejectedValueOnce({ response: { data: { message: 'Backend down' } } });
    renderFocus('open');
    expect(await screen.findByText('Backend down')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Retry' }));
    expect(await screen.findByText('Beta task')).toBeTruthy();
  });
});
