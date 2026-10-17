import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Project } from '../../api/work';
import type { Member } from '../../pages/Members';
import { ProjectFormModal } from './ProjectFormModal';

const { create, update } = vi.hoisted(() => ({ create: vi.fn(), update: vi.fn() }));

vi.mock('../../api/work', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../api/work')>();
  return { ...actual, projectApi: { ...actual.projectApi, create, update } };
});

vi.mock('../../contexts/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'u-me', name: 'Me Myself', email: 'me@example.test' } }),
}));

const members: Member[] = [
  { id: 'm1', userId: 'u-me', name: 'Me Myself', email: 'me@example.test', role: 'ADMIN', joinedAt: '' },
  { id: 'm2', userId: 'u-lead', name: 'Lena Lead', email: 'lena@example.test', role: 'MEMBER', joinedAt: '' },
];

const project = (overrides: Partial<Project> = {}): Project => ({
  id: 'p1',
  tenantId: 't1',
  name: 'Apollo',
  description: 'Moonshot',
  status: 'ACTIVE',
  createdById: 'u-me',
  createdByName: 'Me Myself',
  ownerId: 'u-me',
  ownerName: 'Me Myself',
  taskCount: 0,
  completedTaskCount: 0,
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
  version: 3,
  ...overrides,
});

const renderModal = (p: Project | null, onSaved = vi.fn()) =>
  render(<ProjectFormModal isOpen onClose={vi.fn()} tenantId="t1" project={p} members={members} onSaved={onSaved} />);

const ownerSelect = () => screen.getByLabelText('Owner') as HTMLSelectElement;

describe('ProjectFormModal owner selection', () => {
  beforeEach(() => {
    create.mockReset().mockImplementation((_t: string, input: { name: string }) => Promise.resolve(project({ name: input.name })));
    update.mockReset().mockImplementation(() => Promise.resolve(project()));
  });

  it('offers only workspace members (plus Unassigned) as owners', () => {
    renderModal(null);
    const labels = [...ownerSelect().options].map((o) => o.textContent);
    expect(labels).toEqual(['Unassigned', 'Me Myself', 'Lena Lead']);
  });

  it('defaults a new project to the current user and sends that owner', async () => {
    const user = userEvent.setup();
    renderModal(null);
    expect(ownerSelect().value).toBe('u-me');

    await user.type(screen.getByLabelText('Name'), 'New project');
    await user.click(screen.getByRole('button', { name: 'Create project' }));

    await waitFor(() => expect(create).toHaveBeenCalledTimes(1));
    expect(create.mock.calls[0][1]).toMatchObject({ name: 'New project', ownerId: 'u-me' });
  });

  it('preselects the current owner and keeps it when editing other fields', async () => {
    const user = userEvent.setup();
    renderModal(project({ ownerId: 'u-lead', ownerName: 'Lena Lead' }));
    expect(ownerSelect().value).toBe('u-lead');

    await user.clear(screen.getByLabelText('Name'));
    await user.type(screen.getByLabelText('Name'), 'Renamed');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(update).toHaveBeenCalledTimes(1));
    expect(update.mock.calls[0][2]).toMatchObject({ name: 'Renamed', ownerId: 'u-lead', version: 3 });
  });

  it('never unassigns the owner just because the member list has not loaded', async () => {
    const user = userEvent.setup();
    render(<ProjectFormModal isOpen onClose={vi.fn()} tenantId="t1" project={project({ ownerId: 'u-lead', ownerName: 'Lena Lead' })} members={[]} onSaved={vi.fn()} />);
    // The current owner stays visible and selected even though no members are known yet.
    expect(ownerSelect().value).toBe('u-lead');
    expect([...ownerSelect().options].map((o) => o.textContent)).toContain('Lena Lead');

    await user.click(screen.getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(update).toHaveBeenCalledTimes(1));
    expect(update.mock.calls[0][2]).toHaveProperty('ownerId', 'u-lead');
  });

  it('reassigns to another member', async () => {
    const user = userEvent.setup();
    const onSaved = vi.fn();
    renderModal(project(), onSaved);

    await user.selectOptions(ownerSelect(), 'u-lead');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(update).toHaveBeenCalledTimes(1));
    expect(update.mock.calls[0][2]).toMatchObject({ ownerId: 'u-lead' });
    expect(onSaved).toHaveBeenCalled();
  });

  it('unassigns by sending an explicit null', async () => {
    const user = userEvent.setup();
    renderModal(project());

    await user.selectOptions(ownerSelect(), '');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() => expect(update).toHaveBeenCalledTimes(1));
    expect(update.mock.calls[0][2]).toHaveProperty('ownerId', null);
  });

  it('shows an unassigned project as Unassigned', () => {
    renderModal(project({ ownerId: null, ownerName: null }));
    expect(ownerSelect().value).toBe('');
    expect(ownerSelect().selectedOptions[0].textContent).toBe('Unassigned');
  });

  it('shows the backend rejection of an ineligible owner', async () => {
    const user = userEvent.setup();
    update.mockRejectedValueOnce({ response: { data: { message: 'Project owner must be a member of this workspace' } } });
    renderModal(project());

    await user.selectOptions(ownerSelect(), 'u-lead');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByText('Project owner must be a member of this workspace')).toBeTruthy();
  });
});
