import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import client from '../api/client';
import { useTenant } from '../contexts/TenantContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Modal } from '../components/ui/Modal';
import { Skeleton } from '../components/ui/Skeleton';
import { ErrorState } from '../components/ui/ErrorState';
import { EmptyState } from '../components/ui/EmptyState';
import { FolderKanban, CheckSquare, Users, Clock } from 'lucide-react';

interface DashboardData {
  totalProjects: number;
  totalTasks: number;
  todoTasks: number;
  inProgressTasks: number;
  doneTasks: number;
  totalMembers: number;
  currentUserRole: string;
}

const Dashboard = () => {
  const { currentTenant, tenants, addTenant } = useTenant();
  const navigate = useNavigate();
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Create workspace modal state — kept in Dashboard to avoid remount
  const [showCreate, setShowCreate] = useState(false);
  const [newTenantName, setNewTenantName] = useState('');
  const [newTenantSlug, setNewTenantSlug] = useState('');
  const [createError, setCreateError] = useState('');
  const [createLoading, setCreateLoading] = useState(false);

  useEffect(() => {
    if (currentTenant) {
      fetchDashboard();
    } else {
      setLoading(false);
    }
  }, [currentTenant?.id]);

  const fetchDashboard = async () => {
    if (!currentTenant) return;
    setLoading(true);
    setError('');
    try {
      const res = await client.get(`/api/tenants/${currentTenant.id}/dashboard`);
      setData(res.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load dashboard data');
    } finally {
      setLoading(false);
    }
  };

  const handleOpenCreate = () => {
    setNewTenantName('');
    setNewTenantSlug('');
    setCreateError('');
    setShowCreate(true);
  };

  const handleCloseCreate = () => {
    setShowCreate(false);
    setCreateError('');
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreateError('');
    setCreateLoading(true);
    try {
      const res = await client.post('/api/tenants', { name: newTenantName, slug: newTenantSlug });
      const created = res.data;
      // Use addTenant to immediately add to context without a re-fetch
      // that would cause re-renders while modal is open
      addTenant({
        id: created.id,
        name: created.name,
        slug: created.slug,
        myRole: created.myRole,
      });
      setShowCreate(false);
      // Navigate to the new tenant's dashboard
      navigate(`/tenants/${created.id}/dashboard`);
    } catch (err: any) {
      if (err.response?.data?.fieldErrors && Object.keys(err.response.data.fieldErrors).length > 0) {
        const msgs = Object.values(err.response.data.fieldErrors as Record<string, string>).join('; ');
        setCreateError(msgs);
      } else {
        setCreateError(err.response?.data?.message || 'Failed to create workspace');
      }
    } finally {
      setCreateLoading(false);
    }
  };

  // Modal is rendered as a JSX expression (NOT as an inner component function)
  // to prevent React from remounting inputs on each parent state update.
  const createWorkspaceModal = (
    <Modal isOpen={showCreate} onClose={handleCloseCreate} title="Create New Workspace">
      <form id="create-ws-form" onSubmit={handleCreate}>
        <ErrorState message={createError} />
        <Input
          label="Workspace Name"
          value={newTenantName}
          onChange={(e) => setNewTenantName(e.target.value)}
          required
          placeholder="Acme Corp"
          autoComplete="off"
        />
        <Input
          label="Slug (URL identifier)"
          value={newTenantSlug}
          onChange={(e) => setNewTenantSlug(e.target.value)}
          required
          pattern="^[a-z0-9]+(?:-[a-z0-9]+)*$"
          title="Lowercase letters, numbers, and hyphens only (e.g. acme-corp)"
          placeholder="acme-corp"
          autoComplete="off"
        />
        <p className="text-xs text-muted">Lowercase letters, numbers, and hyphens only. E.g. "acme-corp"</p>
      </form>
      <div className="flex justify-end gap-2 mt-4 pt-4" style={{ borderTop: '1px solid var(--border-color)' }}>
        <Button variant="ghost" onClick={handleCloseCreate} type="button">Cancel</Button>
        <Button type="submit" form="create-ws-form" isLoading={createLoading} disabled={createLoading}>
          {createLoading ? 'Creating...' : 'Create Workspace'}
        </Button>
      </div>
    </Modal>
  );

  if (!currentTenant && tenants.length === 0) {
    return (
      <div className="max-w-4xl mx-auto">
        <EmptyState
          icon={<FolderKanban size={48} />}
          title="Welcome to TenantFlow"
          description="You don't belong to any workspaces yet. Create one to get started and invite your team."
          action={<Button onClick={handleOpenCreate}>Create Workspace</Button>}
        />
        {createWorkspaceModal}
      </div>
    );
  }

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="mb-1">{currentTenant?.name || 'Dashboard'} Overview</h1>
          <p className="text-muted">{new Date().toLocaleDateString(undefined, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}</p>
        </div>
        <div className="flex gap-2">
          <Button onClick={handleOpenCreate}>+ New Workspace</Button>
          {currentTenant && (
            <Button variant="secondary" onClick={fetchDashboard}>Refresh</Button>
          )}
        </div>
      </div>

      {createWorkspaceModal}

      <ErrorState message={error} />

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <Skeleton style={{ height: '120px' }} />
          <Skeleton style={{ height: '120px' }} />
          <Skeleton style={{ height: '120px' }} />
        </div>
      ) : data ? (
        <>
          <div className="grid grid-cols-3 gap-4 mb-6">
            <div className="card flex items-center gap-4">
              <div style={{ padding: '1rem', backgroundColor: 'var(--primary-light)', color: 'var(--primary-color)', borderRadius: 'var(--radius-lg)' }}>
                <FolderKanban size={24} />
              </div>
              <div>
                <p className="text-muted text-sm font-medium">Total Projects</p>
                <h2 className="mb-0">{data.totalProjects}</h2>
              </div>
            </div>

            <div className="card flex items-center gap-4">
              <div style={{ padding: '1rem', backgroundColor: 'var(--success-bg)', color: 'var(--success-text)', borderRadius: 'var(--radius-lg)' }}>
                <CheckSquare size={24} />
              </div>
              <div>
                <p className="text-muted text-sm font-medium">Total Tasks</p>
                <h2 className="mb-0">{data.totalTasks}</h2>
              </div>
            </div>

            <div className="card flex items-center gap-4">
              <div style={{ padding: '1rem', backgroundColor: 'var(--warning-bg)', color: 'var(--warning-text)', borderRadius: 'var(--radius-lg)' }}>
                <Users size={24} />
              </div>
              <div>
                <p className="text-muted text-sm font-medium">Team Members</p>
                <h2 className="mb-0">{data.totalMembers}</h2>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-6">
            <div className="card">
              <h3 className="mb-4 flex items-center gap-2"><Clock size={18} /> Task Status</h3>
              <div className="flex flex-col gap-3">
                <div className="flex justify-between items-center p-3" style={{ backgroundColor: 'var(--bg-subtle)', borderRadius: 'var(--radius-md)' }}>
                  <div className="flex items-center gap-2">
                    <div style={{ width: 10, height: 10, borderRadius: '50%', backgroundColor: 'var(--text-muted)' }}></div>
                    <span className="font-medium">To Do</span>
                  </div>
                  <span className="font-semibold">{data.todoTasks}</span>
                </div>

                <div className="flex justify-between items-center p-3" style={{ backgroundColor: 'var(--primary-light)', borderRadius: 'var(--radius-md)' }}>
                  <div className="flex items-center gap-2">
                    <div style={{ width: 10, height: 10, borderRadius: '50%', backgroundColor: 'var(--primary-color)' }}></div>
                    <span className="font-medium" style={{ color: 'var(--primary-color)' }}>In Progress</span>
                  </div>
                  <span className="font-semibold" style={{ color: 'var(--primary-color)' }}>{data.inProgressTasks}</span>
                </div>

                <div className="flex justify-between items-center p-3" style={{ backgroundColor: 'var(--success-bg)', borderRadius: 'var(--radius-md)' }}>
                  <div className="flex items-center gap-2">
                    <div style={{ width: 10, height: 10, borderRadius: '50%', backgroundColor: 'var(--success-color)' }}></div>
                    <span className="font-medium" style={{ color: 'var(--success-text)' }}>Done</span>
                  </div>
                  <span className="font-semibold" style={{ color: 'var(--success-text)' }}>{data.doneTasks}</span>
                </div>
              </div>
            </div>

            <div className="card">
              <h3 className="mb-4">Quick Actions</h3>
              <div className="flex flex-col gap-2">
                <Link to={`/tenants/${currentTenant?.id}/projects`} className="btn btn-secondary w-full justify-start">
                  View Projects
                </Link>
                <Link to={`/tenants/${currentTenant?.id}/tasks`} className="btn btn-secondary w-full justify-start">
                  Manage Tasks
                </Link>
                <Link to={`/tenants/${currentTenant?.id}/members`} className="btn btn-secondary w-full justify-start">
                  Team Members
                </Link>
              </div>
            </div>
          </div>
        </>
      ) : !loading && currentTenant ? (
        <EmptyState
          icon={<FolderKanban size={48} />}
          title="No data yet"
          description="This workspace has no projects or tasks yet."
          action={<Link to={`/tenants/${currentTenant.id}/projects`} className="btn btn-primary">Create First Project</Link>}
        />
      ) : null}
    </div>
  );
};

export default Dashboard;
