import { Link } from 'react-router-dom';
import React, { useEffect, useState } from 'react';
import client from '../api/client';
import { useTenant } from '../contexts/TenantContext';
import { useAuth } from '../contexts/AuthContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Modal } from '../components/ui/Modal';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { RoleBadge } from '../components/ui/Badge';
import { Avatar } from '../components/ui/Avatar';
import { Table } from '../components/ui/Table';
import { TableSkeleton } from '../components/ui/Skeleton';
import { ErrorState } from '../components/ui/ErrorState';
import { EmptyState } from '../components/ui/EmptyState';
import { PageHeader } from '../components/ui/PageHeader';
import { useToast } from '../contexts/ToastContext';
import { Users, UserPlus, Trash2 } from 'lucide-react';

export interface Member {
  id: string;
  userId: string;
  name: string;
  email: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER';
  joinedAt: string;
  /** Open tasks assigned to this member (member list only). */
  openTasks?: number | null;
  /** Of those, how many are overdue (member list only). */
  overdueTasks?: number | null;
}

const Members = () => {
  const { currentTenant } = useTenant();
  const { user } = useAuth();
  const toast = useToast();
  const [members, setMembers] = useState<Member[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');

  const [showAdd, setShowAdd] = useState(false);
  const [addEmail, setAddEmail] = useState('');
  const [addRole, setAddRole] = useState<'ADMIN' | 'MEMBER' | 'OWNER'>('MEMBER');
  const [addError, setAddError] = useState('');
  const [addLoading, setAddLoading] = useState(false);

  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const canManage = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';
  const isOwner = currentTenant?.myRole === 'OWNER';

  useEffect(() => {
    fetchMembers();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentTenant?.id]);

  const fetchMembers = async () => {
    if (!currentTenant) return;
    setLoading(true);
    setError('');
    try {
      const res = await client.get(`/api/tenants/${currentTenant.id}/members`);
      setMembers(res.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load members');
    } finally {
      setLoading(false);
    }
  };

  const openAdd = () => {
    setAddEmail('');
    setAddRole('MEMBER');
    setAddError('');
    setShowAdd(true);
  };

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentTenant) return;
    setAddError('');
    setAddLoading(true);
    try {
      await client.post(`/api/tenants/${currentTenant.id}/members`, {
        email: addEmail,
        role: addRole,
      });
      setShowAdd(false);
      toast.success(`${addEmail} added to ${currentTenant.name}`);
      fetchMembers();
    } catch (err: any) {
      setAddError(err.response?.data?.message || 'Failed to add member');
    } finally {
      setAddLoading(false);
    }
  };

  const handleRoleChange = async (memberId: string, newRole: string) => {
    if (!currentTenant) return;
    setActionError('');
    try {
      await client.put(`/api/tenants/${currentTenant.id}/members/${memberId}`, { role: newRole });
      toast.success('Role updated');
      fetchMembers();
    } catch (err: any) {
      setActionError(err.response?.data?.message || 'Failed to update role');
    }
  };

  const handleRemove = async () => {
    if (!currentTenant || !deleteId) return;
    setDeleteLoading(true);
    setActionError('');
    try {
      await client.delete(`/api/tenants/${currentTenant.id}/members/${deleteId}`);
      setDeleteId(null);
      toast.success('Member removed');
      fetchMembers();
    } catch (err: any) {
      setActionError(err.response?.data?.message || 'Failed to remove member');
    } finally {
      setDeleteLoading(false);
    }
  };

  if (!currentTenant) return null;

  const removing = members.find((m) => m.id === deleteId);
  const roleCounts = (['OWNER', 'ADMIN', 'MEMBER'] as const).map((role) => ({
    role,
    count: members.filter((m) => m.role === role).length,
  }));
  const ROLE_PLURAL = { OWNER: 'Owners', ADMIN: 'Admins', MEMBER: 'Members' } as const;

  return (
    <div>
      <PageHeader
        title="Members"
        subtitle={`People with access to ${currentTenant.name}, what they can do and what is on their plate.`}
        actions={
          canManage ? (
            <Button onClick={openAdd}>
              <UserPlus size={16} /> Add member
            </Button>
          ) : undefined
        }
      />

      <ErrorState message={error} />
      <ErrorState message={actionError} />

      <Modal isOpen={showAdd} onClose={() => setShowAdd(false)} title="Add member">
        <form id="add-member-form" onSubmit={handleAdd} noValidate>
          <ErrorState message={addError} />
          <Input
            label="Email address"
            type="email"
            value={addEmail}
            onChange={(e) => setAddEmail(e.target.value)}
            required
            placeholder="colleague@example.com"
            hint="This person must already have a TenantFlow account with this email address — there's no email invitation yet."
            autoFocus
          />
          <Select
            label="Role"
            value={addRole}
            onChange={(e) => setAddRole(e.target.value as any)}
            options={[
              { value: 'MEMBER', label: 'Member — can view and manage tasks' },
              { value: 'ADMIN', label: 'Admin — can manage projects and members' },
              ...(isOwner ? [{ value: 'OWNER', label: 'Owner — full access' }] : []),
            ]}
          />
        </form>
        <div className="modal-actions">
          <Button variant="secondary" onClick={() => setShowAdd(false)}>
            Cancel
          </Button>
          <Button type="submit" form="add-member-form" isLoading={addLoading}>
            Add member
          </Button>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!deleteId}
        title="Remove member"
        message={`Remove ${removing?.name ?? 'this member'} from ${currentTenant.name}? They lose access immediately, and projects they own become unassigned.`}
        confirmText="Remove member"
        isDestructive
        isLoading={deleteLoading}
        onConfirm={handleRemove}
        onCancel={() => setDeleteId(null)}
      />

      {loading ? (
        <TableSkeleton rows={4} cols={5} />
      ) : members.length === 0 ? (
        <EmptyState
          icon={<Users size={24} />}
          title="No members found"
          description="It's quiet here. Add team members to collaborate."
          action={canManage ? <Button onClick={openAdd}>Add member</Button> : undefined}
        />
      ) : (
        <>
          <div className="role-summary" aria-label="Members by role">
            <span>
              <strong>{members.length}</strong> {members.length === 1 ? 'person' : 'people'}
            </span>
            {roleCounts
              .filter((rc) => rc.count > 0)
              .map((rc) => (
                <span key={rc.role}>
                  <strong>{rc.count}</strong> {rc.count === 1 ? ROLE_PLURAL[rc.role].slice(0, -1) : ROLE_PLURAL[rc.role]}
                </span>
              ))}
          </div>
          <details className="role-guide">
            <summary>What can each role do?</summary>
            <dl>
              <div><dt>Member</dt><dd>Views everything in the workspace; creates, edits and comments on tasks; uploads files.</dd></div>
              <div><dt>Admin</dt><dd>Everything a member can, plus creating, editing and deleting projects and tasks, and managing members.</dd></div>
              <div><dt>Owner</dt><dd>Everything an admin can, plus managing owners and deleting the workspace.</dd></div>
            </dl>
          </details>
          <Table>
            <thead>
              <tr>
                <th>Person</th>
                <th>Role</th>
                <th>Open work</th>
                <th>Joined</th>
                {canManage && (
                  <th style={{ width: '64px' }}>
                    <span className="sr-only">Actions</span>
                  </th>
                )}
              </tr>
            </thead>
            <tbody>
              {members.map((m) => {
                const isSelf = m.userId === user?.id;
                const isHigherRole = m.role === 'OWNER' && !isOwner;
                const isDisabled = isSelf || isHigherRole;

                return (
                  <tr key={m.id}>
                    <td>
                      <div className="member-cell">
                        <Avatar name={m.name} size="md" />
                        <div style={{ minWidth: 0 }}>
                          <div className="member-name">
                            <Link to={`/tenants/${currentTenant.id}/members/${m.userId}`} className="truncate table-primary-link">{m.name}</Link>
                            {isSelf && <span className="you-badge">You</span>}
                          </div>
                          <div className="member-email truncate">{m.email}</div>
                        </div>
                      </div>
                    </td>
                    <td>
                      {canManage && !isDisabled ? (
                        <select value={m.role} onChange={(e) => handleRoleChange(m.id, e.target.value)} aria-label={`Role of ${m.name}`}>
                          <option value="MEMBER">Member</option>
                          <option value="ADMIN">Admin</option>
                          {(isOwner || m.role === 'OWNER') && <option value="OWNER">Owner</option>}
                        </select>
                      ) : (
                        <RoleBadge role={m.role} />
                      )}
                    </td>
                    <td>
                      {m.openTasks == null ? (
                        <span className="text-faint text-sm">—</span>
                      ) : m.openTasks === 0 ? (
                        <span className="text-faint text-sm">No open tasks</span>
                      ) : (
                        <span className="workload-cell">
                          <span className="tabular font-semibold">{m.openTasks}</span>
                          <span className="text-muted text-sm">open</span>
                          {!!m.overdueTasks && <span className="badge badge-red">{m.overdueTasks} overdue</span>}
                        </span>
                      )}
                    </td>
                    <td className="text-muted text-sm">
                      {new Date(m.joinedAt).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })}
                    </td>
                    {canManage && (
                      <td>
                        {!isDisabled && (
                          <Button
                            variant="ghost"
                            size="sm"
                            className="btn-icon text-danger"
                            onClick={() => setDeleteId(m.id)}
                            aria-label={`Remove ${m.name}`}
                            data-tooltip="Remove"
                          >
                            <Trash2 size={15} />
                          </Button>
                        )}
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </Table>
        </>
      )}
    </div>
  );
};


export default Members;
