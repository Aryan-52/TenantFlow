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
import { Users, UserPlus, Trash2 } from 'lucide-react';

export interface Member {
  id: string;
  userId: string;
  name: string;
  email: string;
  role: 'OWNER' | 'ADMIN' | 'MEMBER';
  joinedAt: string;
}

const Members = () => {
  const { currentTenant } = useTenant();
  const { user } = useAuth();
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
      fetchMembers();
    } catch (err: any) {
      setActionError(err.response?.data?.message || 'Failed to remove member');
    } finally {
      setDeleteLoading(false);
    }
  };

  if (!currentTenant) return null;

  return (
    <div>
      <PageHeader
        title="Team members"
        subtitle={`Manage who has access to ${currentTenant.name}.`}
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
        <div className="flex justify-end gap-2">
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
        message="Are you sure you want to remove this member from the workspace? They will lose all access."
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
        <Table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Joined</th>
              {canManage && <th style={{ width: '80px' }}>Actions</th>}
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
                    <div className="flex items-center gap-2">
                      <Avatar name={m.name} size="sm" />
                      <span className="font-medium">{m.name}</span>
                      {isSelf && <RoleBadgeYou />}
                    </div>
                  </td>
                  <td className="text-muted">{m.email}</td>
                  <td>
                    {canManage && !isDisabled ? (
                      <select value={m.role} onChange={(e) => handleRoleChange(m.id, e.target.value)} style={{ padding: '0.25rem 1.5rem 0.25rem 0.5rem', fontSize: 'var(--text-sm)' }}>
                        <option value="MEMBER">Member</option>
                        <option value="ADMIN">Admin</option>
                        {(isOwner || m.role === 'OWNER') && <option value="OWNER">Owner</option>}
                      </select>
                    ) : (
                      <RoleBadge role={m.role} />
                    )}
                  </td>
                  <td className="text-muted text-sm">{new Date(m.joinedAt).toLocaleDateString()}</td>
                  {canManage && (
                    <td>
                      {!isDisabled && (
                        <Button variant="ghost" size="sm" className="btn-icon text-danger" onClick={() => setDeleteId(m.id)} title="Remove member">
                          <Trash2 size={16} />
                        </Button>
                      )}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </Table>
      )}
    </div>
  );
};

const RoleBadgeYou = () => <span className="badge badge-blue">You</span>;

export default Members;
