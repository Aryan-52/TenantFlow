import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { useState } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Task } from '../../api/work';
import type { Dependencies } from '../../api/planning';
import { addDays, toDateKey } from '../tasks/taskMeta';
import { ProjectTimeline } from './ProjectTimeline';
import { TaskDependencies } from '../tasks/TaskDependencies';
import { MentionTextarea } from '../tasks/MentionTextarea';
import { mentionedIds, renderMentions } from '../tasks/mentions';
import { Sidebar } from '../layout/Sidebar';

const { depGet, depAdd, depRemove, lookup, setCurrentTenantId, tenantState } = vi.hoisted(() => ({
  depGet: vi.fn(),
  depAdd: vi.fn(),
  depRemove: vi.fn(),
  lookup: vi.fn(),
  setCurrentTenantId: vi.fn(),
  tenantState: {
    tenants: [
      { id: 'w1', name: 'Acme', slug: 'acme', myRole: 'MEMBER' },
      { id: 'w2', name: 'Beta Labs', slug: 'beta', myRole: 'ADMIN' },
    ],
  },
}));

vi.mock('../../api/planning', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/planning')>()),
  dependencyApi: { get: depGet, add: depAdd, remove: depRemove, lookup },
}));
vi.mock('../../contexts/TenantContext', () => ({
  useTenant: () => ({ currentTenant: tenantState.tenants[0], tenants: tenantState.tenants, setCurrentTenantId }),
}));

const day = (n: number) => toDateKey(addDays(new Date(), n));
const task = (id: string, title: string, o: Partial<Task> = {}): Task => ({
  id, projectId: 'p1', projectName: 'Apollo', title, description: null, status: 'TODO', priority: 'MEDIUM',
  assigneeId: null, assigneeName: null, createdById: null, createdByName: null, dueDate: null,
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', version: 0, ...o,
});

describe('ProjectTimeline', () => {
  it('draws bars from real dates, marks blocked work and lists unscheduled tasks', () => {
    render(
      <MemoryRouter>
        <ProjectTimeline
          tenantId="t1"
          tasks={[
            task('a', 'Design', { startDate: day(0), dueDate: day(4), status: 'IN_PROGRESS' }),
            task('b', 'Build', { dueDate: day(6), openBlockers: 1 }),
            task('c', 'Someday'),
          ]}
          milestones={[{ id: 'm', projectId: 'p1', projectName: 'Apollo', name: 'Beta', description: null, targetDate: day(5), status: 'OPEN', completedAt: null, totalTasks: 0, doneTasks: 0, version: 0 }]}
        />
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: /^Design: starts .*due .*In progress$/i })).toBeTruthy();
    expect(screen.getByRole('link', { name: /^Build: due .*blocked by 1 task$/ })).toBeTruthy();
    expect(screen.getByRole('link', { name: /Milestone Beta/ })).toBeTruthy();
    expect(screen.getByRole('heading', { name: /Not scheduled/ })).toBeTruthy();
    expect(screen.getByRole('link', { name: 'Someday' })).toBeTruthy();
  });
});

describe('TaskDependencies', () => {
  const deps = (o: Partial<Dependencies>): Dependencies => ({ blockedBy: [], blocks: [], openBlockers: 0, ...o });
  beforeEach(() => {
    depGet.mockReset().mockResolvedValue(deps({}));
    lookup.mockReset().mockResolvedValue([
      { id: 'x', projectId: 'p1', projectName: 'Apollo', title: 'Write API', status: 'TODO' },
      { id: 'self', projectId: 'p1', projectName: 'Apollo', title: 'This task', status: 'TODO' },
    ]);
  });

  it('adds a blocker with the keyboard, explains the blocked state, and shows server refusals', async () => {
    const user = userEvent.setup();
    depAdd.mockResolvedValueOnce(deps({
      openBlockers: 1,
      blockedBy: [{ dependencyId: 'd1', id: 'x', projectId: 'p1', projectName: 'Apollo', title: 'Write API', status: 'TODO', dueDate: null, assigneeName: null }],
    }));
    render(<MemoryRouter><TaskDependencies tenantId="t1" taskId="self" /></MemoryRouter>);
    const box = screen.getByRole('combobox', { name: 'Find a task that blocks this one' });
    await user.type(box, 'API');
    expect(await screen.findByRole('option', { name: /Write API/ })).toBeTruthy();
    expect(screen.queryByRole('option', { name: /This task/ })).toBeNull(); // never itself
    await user.keyboard('{Enter}');
    expect(depAdd).toHaveBeenCalledWith('t1', 'self', 'x');
    expect(await screen.findByText(/Blocked: waiting on 1 unfinished task/)).toBeTruthy();

    depRemove.mockRejectedValueOnce({ response: { data: { message: 'Dependency not found' } } });
    await user.click(screen.getByRole('button', { name: 'Remove dependency on Write API' }));
    expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'Dependency not found');
  });
});

describe('Mentions', () => {
  const members = [{ userId: 'u1', name: 'Ada Lovelace' }, { userId: 'u2', name: 'Max' }];

  it('derives mentions from the text, like the server', () => {
    expect(mentionedIds('Thanks @ada lovelace and @Max!', members)).toEqual(['u1', 'u2']);
    expect(mentionedIds('email max@example.com', members)).toEqual([]);
    render(<p>{renderMentions('ping @Max now', members)}</p>);
    expect(screen.getByText('@Max').className).toBe('mention');
  });

  it('suggests members after @ and inserts the name', async () => {
    const user = userEvent.setup();
    const Harness = () => {
      const [v, setV] = useState('');
      return <MentionTextarea aria-label="Comment" value={v} onChange={setV} members={members} />;
    };
    render(<Harness />);
    const box = screen.getByRole('combobox', { name: 'Comment' }) as HTMLTextAreaElement;
    await user.type(box, 'Hi @ad');
    expect(screen.getByRole('option', { name: /Ada Lovelace/ })).toBeTruthy();
    await user.keyboard('{Enter}');
    expect(box.value).toBe('Hi @Ada Lovelace ');
    expect(screen.queryByRole('listbox')).toBeNull();
  });
});

describe('Sidebar', () => {
  const Where = () => <div data-testid="where">{useLocation().pathname}</div>;
  beforeEach(() => {
    localStorage.clear();
    setCurrentTenantId.mockReset();
  });

  const renderSidebar = () =>
    render(
      <MemoryRouter initialEntries={['/tenants/w1/my-work']}>
        <Routes>
          <Route path="*" element={<><Sidebar mobileOpen={false} onNavigate={vi.fn()} collapsed={false} onToggleCollapsed={vi.fn()} /><Where /></>} />
        </Routes>
      </MemoryRouter>,
    );

  it('groups destinations, marks the current page and hides admin-only items from members', () => {
    renderSidebar();
    expect(screen.getByRole('link', { name: 'My Work' }).getAttribute('aria-current')).toBe('page');
    for (const name of ['Overview', 'Portfolio', 'Planning', 'Calendar', 'Documents', 'Automations', 'Reports', 'Preferences']) {
      expect(screen.getByRole('link', { name })).toBeTruthy();
    }
    expect(screen.queryByRole('link', { name: 'Workspace settings' })).toBeNull();
  });

  it('collapses a group (remembered) but never hides the current page', async () => {
    const user = userEvent.setup();
    renderSidebar();
    await user.click(screen.getByRole('button', { name: 'Collaboration' }));
    expect(screen.queryByRole('link', { name: 'Documents' })).toBeNull();
    expect(JSON.parse(localStorage.getItem('tf.navGroupsClosed')!)).toEqual(['collaboration']);
    await user.click(screen.getByRole('button', { name: 'Workspace' }));
    expect(screen.getByRole('link', { name: 'My Work' })).toBeTruthy(); // holds the current page
  });

  it('switches workspace with the role shown and lands on its overview', async () => {
    const user = userEvent.setup();
    renderSidebar();
    await user.click(screen.getByRole('button', { name: /Workspace: Acme, your role Member/ }));
    await user.click(screen.getByRole('button', { name: /Beta Labs/ }));
    expect(setCurrentTenantId).toHaveBeenCalledWith('w2');
    expect(screen.getByTestId('where').textContent).toBe('/tenants/w2/dashboard');
  });
});
