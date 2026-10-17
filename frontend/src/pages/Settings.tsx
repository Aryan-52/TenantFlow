import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import client from '../api/client';
import { useTenant, type Tenant } from '../contexts/TenantContext';
import { useToast } from '../contexts/ToastContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { RoleBadge } from '../components/ui/Badge';
import { ErrorState } from '../components/ui/ErrorState';
import { Modal } from '../components/ui/Modal';
import { PageHeader } from '../components/ui/PageHeader';
import { initials, tintStyle } from '../components/ui/visual';
import { Lock } from 'lucide-react';

const WorkspaceSettings = ({ tenant }: { tenant: Tenant }) => {
  const { refreshTenants } = useTenant();
  const toast = useToast();
  const navigate = useNavigate();

  // Remounted per workspace (see SettingsPage), so the field starts from that workspace's name.
  const [name, setName] = useState(tenant.name);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const [showDelete, setShowDelete] = useState(false);
  const [deleteConfirmSlug, setDeleteConfirmSlug] = useState('');
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [deleteError, setDeleteError] = useState('');

  const canManage = tenant.myRole === 'ADMIN' || tenant.myRole === 'OWNER';
  const isOwner = tenant.myRole === 'OWNER';

  if (!canManage) {
    return <ErrorState message="You do not have permission to view workspace settings." />;
  }

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    try {
      await client.put(`/api/tenants/${tenant.id}`, { name });
      await refreshTenants();
      toast.success('Workspace updated');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to update workspace');
    } finally {
      setLoading(false);
    }
  };

  const closeDelete = () => {
    setShowDelete(false);
    setDeleteConfirmSlug('');
    setDeleteError('');
  };

  const handleDelete = async () => {
    if (deleteConfirmSlug !== tenant.slug) {
      setDeleteError('Slug does not match.');
      return;
    }

    setDeleteLoading(true);
    setDeleteError('');
    try {
      await client.delete(`/api/tenants/${tenant.id}`);
      await refreshTenants();
      toast.success(`Workspace "${tenant.name}" deleted`);
      navigate('/dashboard');
    } catch (err: any) {
      setDeleteError(err.response?.data?.message || 'Failed to delete workspace');
      setDeleteLoading(false);
    }
  };

  const unchanged = name.trim() === tenant.name;

  return (
    <div className="max-w-4xl">
      <PageHeader title="Workspace settings" subtitle={`How ${tenant.name} is named and identified, and who can remove it.`} />

      <section className="settings-section" aria-labelledby="settings-general">
        <div>
          <h2 id="settings-general" className="settings-section-title">General</h2>
          <p className="settings-section-desc">The workspace name is shown to every member in the sidebar and switcher.</p>
        </div>
        <div className="card card-body">
          <div className="flex items-center gap-4 mb-6">
            <span className="workspace-mark" style={{ ...tintStyle(tenant.id), width: '3rem', height: '3rem', fontSize: 'var(--text-md)' }} aria-hidden="true">
              {initials(name || tenant.name)}
            </span>
            <div>
              <div className="font-semibold">{name || tenant.name}</div>
              <div className="text-sm text-muted flex items-center gap-2 mt-1">
                Your role <RoleBadge role={tenant.myRole as 'OWNER' | 'ADMIN' | 'MEMBER'} />
              </div>
            </div>
          </div>

          <ErrorState message={error} />

          <form onSubmit={handleUpdate} noValidate>
            <Input label="Workspace name" value={name} onChange={(e) => setName(e.target.value)} required placeholder="e.g. Acme Corp" />
            <div className="form-group">
              <label htmlFor="workspace-slug" className="flex items-center gap-2">
                URL slug <span className="readonly-hint"><Lock size={11} aria-hidden="true" /> Read-only</span>
              </label>
              <input id="workspace-slug" value={tenant.slug} readOnly disabled className="font-mono" />
            </div>
            <div className="flex justify-end">
              <Button type="submit" isLoading={loading} disabled={loading || unchanged || !name.trim()}>
                Save changes
              </Button>
            </div>
          </form>
        </div>
      </section>

      {isOwner && (
        <section className="settings-section is-danger" aria-labelledby="settings-danger">
          <div>
            <h2 id="settings-danger" className="settings-section-title">Delete workspace</h2>
            <p className="settings-section-desc">Only owners can do this. It cannot be undone.</p>
          </div>
          <div className="card card-body border-danger">
            <p className="text-sm mb-4">
              Deleting <strong>{tenant.name}</strong> permanently removes all of its projects, tasks, files and memberships.
            </p>
            <Button variant="danger-outline" onClick={() => setShowDelete(true)}>
              Delete workspace…
            </Button>
          </div>
        </section>
      )}

      <Modal
        isOpen={showDelete}
        onClose={closeDelete}
        title="Delete workspace"
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={closeDelete}>
              Cancel
            </Button>
            <Button variant="danger" onClick={handleDelete} isLoading={deleteLoading} disabled={deleteConfirmSlug !== tenant.slug}>
              Permanently delete
            </Button>
          </>
        }
      >
        <ErrorState message={deleteError} />
        <p className="mb-4">
          This permanently deletes <strong>{tenant.name}</strong> and everything in it. This action cannot be undone.
        </p>
        <div className="form-group mb-0">
          <label htmlFor="delete-confirm-slug">
            Type <strong>{tenant.slug}</strong> to confirm.
          </label>
          <input
            id="delete-confirm-slug"
            type="text"
            value={deleteConfirmSlug}
            onChange={(e) => setDeleteConfirmSlug(e.target.value)}
            placeholder={tenant.slug}
            autoComplete="off"
          />
        </div>
      </Modal>
    </div>
  );
};

const SettingsPage = () => {
  const { currentTenant } = useTenant();
  if (!currentTenant) return null;
  return <WorkspaceSettings key={currentTenant.id} tenant={currentTenant} />;
};

export default SettingsPage;
