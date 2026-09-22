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
import { RoleBadge } from '../components/ui/Badge';
import { PageHeader } from '../components/ui/PageHeader';
import { FolderKanban, CheckSquare, Users, Clock, RefreshCw, Plus } from 'lucide-react';

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
    // eslint-disable-next-line react-hooks/exhaustive-deps
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
      addTenant({
        id: created.id,
        name: created.name,
        slug: created.slug,
        myRole: created.myRole,
      });
      setShowCreate(false);
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
    <Modal isOpen={showCreate} onClose={handleCloseCreate} title="Create new workspace">
      <form id="create-ws-form" onSubmit={handleCreate} noValidate>
        <ErrorState message={createError} />
        <Input
          label="Workspace name"
          value={newTenantName}
          onChange={(e) => setNewTenantName(e.target.value)}
          required
          placeholder="Acme Corp"
          autoComplete="off"
          autoFocus
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
          hint='Lowercase letters, numbers, and hyphens only, e.g. "acme-corp".'
        />
      </form>
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={handleCloseCreate} type="button">
          Cancel
        </Button>
        <Button type="submit" form="create-ws-form" isLoading={createLoading} disabled={createLoading}>
          {createLoading ? 'Creating...' : 'Create workspace'}
        </Button>
      </div>
    </Modal>
  );

  if (!currentTenant && tenants.length === 0) {
    return (
      <div className="max-w-4xl mx-auto">
        <EmptyState
          icon={<FolderKanban size={28} />}
          title="Welcome to TenantFlow"
          description="You don't belong to any workspaces yet. Create one to get started and invite your team."
          action={
            <Button onClick={handleOpenCreate}>
              <Plus size={16} /> Create workspace
            </Button>
          }
        />
        {createWorkspaceModal}
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title={`${currentTenant?.name || 'Dashboard'} overview`}
        subtitle={new Date().toLocaleDateString(undefined, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' })}
        actions={
          <>
            {currentTenant && (
              <Button variant="secondary" onClick={fetchDashboard}>
                <RefreshCw size={15} /> Refresh
              </Button>
            )}
            <Button onClick={handleOpenCreate}>
              <Plus size={16} /> New workspace
            </Button>
          </>
        }
      />

      {createWorkspaceModal}

      <ErrorState message={error} />

      {currentTenant && (
        <div className="flex items-center gap-2 mb-6 text-sm text-muted">
          Your role in this workspace: <RoleBadge role={currentTenant.myRole as 'OWNER' | 'ADMIN' | 'MEMBER'} />
        </div>
      )}

      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
          <Skeleton style={{ height: '100px' }} />
          <Skeleton style={{ height: '100px' }} />
          <Skeleton style={{ height: '100px' }} />
        </div>
      ) : data ? (
        <>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
            <div className="stat-card">
              <div className="stat-card-icon">
                <FolderKanban size={20} />
              </div>
              <div>
                <div className="stat-card-value">{data.totalProjects}</div>
                <div className="stat-card-label">Total projects</div>
              </div>
            </div>

            <div className="stat-card">
              <div className="stat-card-icon" style={{ backgroundColor: 'var(--success-bg)', color: 'var(--success-text)' }}>
                <CheckSquare size={20} />
              </div>
              <div>
                <div className="stat-card-value">{data.totalTasks}</div>
                <div className="stat-card-label">Total tasks</div>
              </div>
            </div>

            <div className="stat-card">
              <div className="stat-card-icon" style={{ backgroundColor: 'var(--warning-bg)', color: 'var(--warning-text)' }}>
                <Users size={20} />
              </div>
              <div>
                <div className="stat-card-value">{data.totalMembers}</div>
                <div className="stat-card-label">Team members</div>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div className="card card-body">
              <h3 className="mb-4 flex items-center gap-2">
                <Clock size={18} /> Task status
              </h3>
              <div className="flex flex-col gap-3">
                <div className="flex justify-between items-center p-3 rounded-md" style={{ backgroundColor: 'var(--bg-subtle)' }}>
                  <span className="flex items-center gap-2 font-medium">
                    <span className="badge-dot" style={{ color: 'var(--text-muted)' }} />
                    To Do
                  </span>
                  <span className="font-semibold">{data.todoTasks}</span>
                </div>

                <div className="flex justify-between items-center p-3 rounded-md" style={{ backgroundColor: 'var(--primary-light)' }}>
                  <span className="flex items-center gap-2 font-medium text-primary">
                    <span className="badge-dot" style={{ color: 'var(--primary-color)' }} />
                    In Progress
                  </span>
                  <span className="font-semibold text-primary">{data.inProgressTasks}</span>
                </div>

                <div className="flex justify-between items-center p-3 rounded-md" style={{ backgroundColor: 'var(--success-bg)' }}>
                  <span className="flex items-center gap-2 font-medium text-success">
                    <span className="badge-dot" style={{ color: 'var(--success-color)' }} />
                    Done
                  </span>
                  <span className="font-semibold text-success">{data.doneTasks}</span>
                </div>
              </div>
            </div>

            <div className="card card-body">
              <h3 className="mb-4">Quick actions</h3>
              <div className="flex flex-col gap-2">
                <Link to={`/tenants/${currentTenant?.id}/projects`} className="btn btn-secondary w-full justify-start">
                  View projects
                </Link>
                <Link to={`/tenants/${currentTenant?.id}/tasks`} className="btn btn-secondary w-full justify-start">
                  Manage tasks
                </Link>
                <Link to={`/tenants/${currentTenant?.id}/members`} className="btn btn-secondary w-full justify-start">
                  Team members
                </Link>
              </div>
            </div>
          </div>
        </>
      ) : !loading && currentTenant ? (
        <EmptyState
          icon={<FolderKanban size={28} />}
          title="No data yet"
          description="This workspace has no projects or tasks yet."
          action={
            <Link to={`/tenants/${currentTenant.id}/projects`} className="btn btn-primary">
              Create first project
            </Link>
          }
        />
      ) : null}
    </div>
  );
};

export default Dashboard;
