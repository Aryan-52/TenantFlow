import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import client from '../api/client';
import Navbar from '../components/Navbar';
import { useAuth } from '../contexts/AuthContext';

interface Tenant {
  id: string;
  name: string;
  slug: string;
  myRole: string;
}

interface Member {
  id: string;
  email: string;
  name: string;
  role: string;
  joinedAt: string;
}

const TenantDetails = () => {
  const { tenantId } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [tenant, setTenant] = useState<Tenant | null>(null);
  const [members, setMembers] = useState<Member[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  // Forms
  const [newName, setNewName] = useState('');
  const [newMemberEmail, setNewMemberEmail] = useState('');
  const [newMemberRole, setNewMemberRole] = useState('MEMBER');

  useEffect(() => {
    fetchTenantData();
  }, [tenantId]);

  const fetchTenantData = async () => {
    try {
      const [tRes, mRes] = await Promise.all([
        client.get(`/api/tenants/${tenantId}`),
        client.get(`/api/tenants/${tenantId}/members`)
      ]);
      setTenant(tRes.data);
      setNewName(tRes.data.name);
      setMembers(mRes.data);
    } catch (err: any) {
      if (err.response?.status === 403) {
        navigate('/');
      }
      setError('Failed to load workspace details');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await client.put(`/api/tenants/${tenantId}`, { name: newName });
      fetchTenantData();
    } catch (err) {
      setError('Failed to update workspace');
    }
  };

  const handleDelete = async () => {
    if (!window.confirm('Are you sure you want to delete this workspace? This cannot be undone.')) return;
    try {
      await client.delete(`/api/tenants/${tenantId}`);
      navigate('/');
    } catch (err) {
      setError('Failed to delete workspace');
    }
  };

  const handleAddMember = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await client.post(`/api/tenants/${tenantId}/members`, {
        email: newMemberEmail,
        role: newMemberRole
      });
      setNewMemberEmail('');
      setNewMemberRole('MEMBER');
      fetchTenantData();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to add member');
    }
  };

  const handleUpdateRole = async (memberId: string, role: string) => {
    try {
      await client.put(`/api/tenants/${tenantId}/members/${memberId}`, { role });
      fetchTenantData();
    } catch (err) {
      setError('Failed to update member role');
    }
  };

  const handleRemoveMember = async (memberId: string) => {
    if (!window.confirm('Remove this member?')) return;
    try {
      await client.delete(`/api/tenants/${tenantId}/members/${memberId}`);
      fetchTenantData();
    } catch (err) {
      setError('Failed to remove member');
    }
  };

  if (loading) return <div>Loading...</div>;
  if (!tenant) return <div>Workspace not found</div>;

  const isAdminOrOwner = tenant.myRole === 'ADMIN' || tenant.myRole === 'OWNER';
  const isOwner = tenant.myRole === 'OWNER';

  return (
    <div className="app-container">
      <Navbar />
      <div className="dashboard-layout">
        <aside className="sidebar">
          <ul className="sidebar-nav">
            <li><a href="#overview" className="sidebar-link active">Overview</a></li>
            <li><a href="#members" className="sidebar-link">Members</a></li>
            {isAdminOrOwner && <li><a href="#settings" className="sidebar-link">Settings</a></li>}
          </ul>
        </aside>
        
        <main className="main-content">
          <h2 className="mb-4">{tenant.name}</h2>
          {error && <div className="alert alert-danger">{error}</div>}

          <section id="members" className="card mb-4">
            <h3 className="mb-4">Members</h3>
            <table className="table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Role</th>
                  <th>Joined</th>
                  {isAdminOrOwner && <th>Actions</th>}
                </tr>
              </thead>
              <tbody>
                {members.map(m => (
                  <tr key={m.id}>
                    <td>{m.name}</td>
                    <td>{m.email}</td>
                    <td>
                      {isAdminOrOwner && m.email !== user?.email ? (
                        <select 
                          value={m.role} 
                          onChange={(e) => handleUpdateRole(m.id, e.target.value)}
                        >
                          <option value="MEMBER">Member</option>
                          <option value="ADMIN">Admin</option>
                          {isOwner && <option value="OWNER">Owner</option>}
                        </select>
                      ) : (
                        m.role
                      )}
                    </td>
                    <td>{new Date(m.joinedAt).toLocaleDateString()}</td>
                    {isAdminOrOwner && (
                      <td>
                        {m.email !== user?.email && (
                          <button className="btn btn-danger" onClick={() => handleRemoveMember(m.id)}>Remove</button>
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>

            {isAdminOrOwner && (
              <div className="mt-4 pt-4" style={{ borderTop: '1px solid var(--border-color)' }}>
                <h4 className="mb-2">Add Member</h4>
                <form onSubmit={handleAddMember} className="flex gap-2">
                  <input
                    type="email"
                    placeholder="User email"
                    value={newMemberEmail}
                    onChange={(e) => setNewMemberEmail(e.target.value)}
                    required
                    style={{ flex: 1 }}
                  />
                  <select
                    value={newMemberRole}
                    onChange={(e) => setNewMemberRole(e.target.value)}
                  >
                    <option value="MEMBER">Member</option>
                    <option value="ADMIN">Admin</option>
                    {isOwner && <option value="OWNER">Owner</option>}
                  </select>
                  <button type="submit" className="btn btn-primary">Add</button>
                </form>
              </div>
            )}
          </section>

          {isAdminOrOwner && (
            <section id="settings" className="card mb-4">
              <h3 className="mb-4">Settings</h3>
              <form onSubmit={handleUpdate} className="mb-4">
                <div className="form-group mb-2">
                  <label>Workspace Name</label>
                  <input
                    type="text"
                    value={newName}
                    onChange={(e) => setNewName(e.target.value)}
                    required
                  />
                </div>
                <button type="submit" className="btn btn-primary">Update Settings</button>
              </form>

              {isOwner && (
                <div className="mt-4 pt-4" style={{ borderTop: '1px solid var(--border-color)' }}>
                  <h4 className="text-danger mb-2">Danger Zone</h4>
                  <p className="mb-2 text-muted">Once you delete a workspace, there is no going back. Please be certain.</p>
                  <button className="btn btn-danger" onClick={handleDelete}>Delete Workspace</button>
                </div>
              )}
            </section>
          )}
        </main>
      </div>
    </div>
  );
};

export default TenantDetails;
