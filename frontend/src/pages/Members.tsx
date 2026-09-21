import React, { useEffect, useState } from 'react';
import client from '../api/client';
import { useTenant } from '../contexts/TenantContext';
import { useAuth } from '../contexts/AuthContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Modal } from '../components/ui/Modal';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { Badge } from '../components/ui/Badge';
import { Table } from '../components/ui/Table';
import { TableSkeleton } from '../components/ui/Skeleton';
import { ErrorState } from '../components/ui/ErrorState';
import { EmptyState } from '../components/ui/EmptyState';
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
  }, [currentTenant]);

  const fetchMembers = async () => {
    if (!currentTenant) return;
    setLoading(true);
    try {
      const res = await client.get(`/api/tenants/${currentTenant.id}/members`);
      setMembers(res.data);
    } catch (err: any) {
      setError('Failed to load members');
    } finally {
      setLoading(false);
    }
  };

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentTenant) return;
    setAddError('');
    setAddLoading(true);
    try {
      await client.post(`/api/tenants/${currentTenant.id}/members`, {
        email: addEmail,
        role: addRole
      });
      setShowAdd(false);
      setAddEmail('');
      setAddRole('MEMBER');
      fetchMembers();
    } catch (err: any) {
      setAddError(err.response?.data?.message || 'Failed to add member');
    } finally {
      setAddLoading(false);
    }
  };

  const handleRoleChange = async (memberId: string, newRole: string) => {
    if (!currentTenant) return;
    try {
      await client.put(`/api/tenants/${currentTenant.id}/members/${memberId}`, { role: newRole });
      fetchMembers();
    } catch (err: any) {
      alert('Failed to update role: ' + (err.response?.data?.message || err.message));
    }
  };

  const handleRemove = async () => {
    if (!currentTenant || !deleteId) return;
    setDeleteLoading(true);
    try {
      await client.delete(`/api/tenants/${currentTenant.id}/members/${deleteId}`);
      setDeleteId(null);
      fetchMembers();
    } catch (err: any) {
      alert('Failed to remove member: ' + (err.response?.data?.message || err.message));
    } finally {
      setDeleteLoading(false);
    }
  };

  if (!currentTenant) return null;

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="mb-1">Team Members</h1>
          <p className="text-muted text-sm">Manage who has access to {currentTenant.name}.</p>
        </div>
        {canManage && (
          <Button onClick={() => setShowAdd(true)}>
            <UserPlus size={16} /> Add Member
          </Button>
        )}
      </div>

      <ErrorState message={error} />

      <Modal 
        isOpen={showAdd} 
        onClose={() => setShowAdd(false)} 
        title="Add Member"
      >
        <form id="add-member-form" onSubmit={handleAdd}>
          <ErrorState message={addError} />
          <Input 
            label="Email Address" 
            type="email"
            value={addEmail} 
            onChange={e => setAddEmail(e.target.value)} 
            required 
            placeholder="colleague@example.com"
          />
          <Select 
            label="Role" 
            value={addRole} 
            onChange={e => setAddRole(e.target.value as any)}
            options={[
              { value: 'MEMBER', label: 'Member - Can view and manage tasks' },
              { value: 'ADMIN', label: 'Admin - Can manage projects and members' },
              ...(isOwner ? [{ value: 'OWNER', label: 'Owner - Full access' }] : [])
            ]}
          />
        </form>
        <div className="flex justify-end gap-2 mt-4 pt-4 border-t border-color" style={{ borderTop: '1px solid var(--border-color)' }}>
          <Button variant="ghost" onClick={() => setShowAdd(false)}>Cancel</Button>
          <Button type="submit" form="add-member-form" isLoading={addLoading}>Add Member</Button>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!deleteId}
        title="Remove Member"
        message="Are you sure you want to remove this member from the workspace? They will lose all access."
        confirmText="Remove Member"
        isDestructive
        isLoading={deleteLoading}
        onConfirm={handleRemove}
        onCancel={() => setDeleteId(null)}
      />

      {loading ? (
        <TableSkeleton rows={4} cols={5} />
      ) : members.length === 0 ? (
        <EmptyState 
          icon={<Users size={48} />}
          title="No members found"
          description="It's quiet here. Invite team members to collaborate."
          action={canManage ? <Button onClick={() => setShowAdd(true)}>Add Member</Button> : undefined}
        />
      ) : (
        <Table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Email</th>
              <th>Role</th>
              <th>Joined</th>
              {canManage && <th>Actions</th>}
            </tr>
          </thead>
          <tbody>
            {members.map(m => {
              const isSelf = m.userId === user?.id;
              const isHigherRole = m.role === 'OWNER' && !isOwner;
              const isDisabled = isSelf || isHigherRole;

              return (
                <tr key={m.id}>
                  <td>
                    <div className="flex items-center gap-2">
                      <div style={{ width: 28, height: 28, borderRadius: '50%', backgroundColor: 'var(--primary-light)', color: 'var(--primary-color)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold', fontSize: '0.75rem' }}>
                        {m.name.charAt(0).toUpperCase()}
                      </div>
                      <span className="font-medium">{m.name}</span>
                      {isSelf && <Badge color="blue">You</Badge>}
                    </div>
                  </td>
                  <td className="text-muted">{m.email}</td>
                  <td>
                    {canManage && !isDisabled ? (
                      <select 
                        value={m.role} 
                        onChange={e => handleRoleChange(m.id, e.target.value)}
                        style={{ padding: '0.25rem 0.5rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}
                      >
                        <option value="MEMBER">Member</option>
                        <option value="ADMIN">Admin</option>
                        {(isOwner || m.role === 'OWNER') && <option value="OWNER">Owner</option>}
                      </select>
                    ) : (
                      <Badge color={m.role === 'OWNER' ? 'red' : m.role === 'ADMIN' ? 'yellow' : 'gray'}>
                        {m.role}
                      </Badge>
                    )}
                  </td>
                  <td className="text-muted text-sm">{new Date(m.joinedAt).toLocaleDateString()}</td>
                  {canManage && (
                    <td>
                      {!isDisabled && (
                        <Button 
                          variant="ghost" 
                          size="sm" 
                          onClick={() => setDeleteId(m.id)}
                          className="text-danger"
                          title="Remove Member"
                        >
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

export default Members;
