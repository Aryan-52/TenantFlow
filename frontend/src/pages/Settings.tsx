import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import client from '../api/client';
import { useTenant } from '../contexts/TenantContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { RoleBadge } from '../components/ui/Badge';
import { ErrorState } from '../components/ui/ErrorState';
import { Alert } from '../components/ui/Alert';
import { Modal } from '../components/ui/Modal';
import { PageHeader } from '../components/ui/PageHeader';
import { Settings as SettingsIcon, ShieldAlert } from 'lucide-react';

const Settings = () => {
  const { currentTenant, refreshTenants } = useTenant();
  const navigate = useNavigate();

  const [name, setName] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  const [showDelete, setShowDelete] = useState(false);
  const [deleteConfirmSlug, setDeleteConfirmSlug] = useState('');
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [deleteError, setDeleteError] = useState('');

  useEffect(() => {
    if (currentTenant) {
      setName(currentTenant.name);
    }
  }, [currentTenant]);

  if (!currentTenant) return null;

  const canManage = currentTenant.myRole === 'ADMIN' || currentTenant.myRole === 'OWNER';
  const isOwner = currentTenant.myRole === 'OWNER';

  if (!canManage) {
    return <ErrorState message="You do not have permission to view workspace settings." />;
  }

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccess('');
    try {
      await client.put(`/api/tenants/${currentTenant.id}`, { name });
      setSuccess('Workspace updated successfully.');
      await refreshTenants();
      setTimeout(() => setSuccess(''), 3000);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to update workspace');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (deleteConfirmSlug !== currentTenant.slug) {
      setDeleteError('Slug does not match.');
      return;
    }

    setDeleteLoading(true);
    setDeleteError('');
    try {
      await client.delete(`/api/tenants/${currentTenant.id}`);
      await refreshTenants();
      navigate('/dashboard');
    } catch (err: any) {
      setDeleteError(err.response?.data?.message || 'Failed to delete workspace');
      setDeleteLoading(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto">
      <PageHeader title="Workspace settings" subtitle={`Manage ${currentTenant.name}'s information and access.`} />

      <div className="card mb-6">
        <div className="card-header">
          <h3 className="mb-0 flex items-center gap-2">
            <SettingsIcon size={18} className="text-muted" /> General information
          </h3>
        </div>
        <div className="card-body">
          <div className="flex gap-6 mb-6">
            <div className="flex-1">
              <p className="text-muted text-sm font-medium mb-2">Your role</p>
              <RoleBadge role={currentTenant.myRole as 'OWNER' | 'ADMIN' | 'MEMBER'} />
            </div>
            <div className="flex-1">
              <p className="text-muted text-sm font-medium mb-2">Workspace slug</p>
              <code className="font-mono text-sm p-2 rounded-md" style={{ backgroundColor: 'var(--bg-subtle)' }}>
                {currentTenant.slug}
              </code>
            </div>
          </div>

          <ErrorState message={error} />
          {success && <Alert variant="success" className="mb-4">{success}</Alert>}

          <form onSubmit={handleUpdate} noValidate>
            <Input label="Workspace name" value={name} onChange={(e) => setName(e.target.value)} required placeholder="e.g. Acme Corp" />
            <Button type="submit" isLoading={loading}>
              Save changes
            </Button>
          </form>
        </div>
      </div>

      {isOwner && (
        <div className="card border-danger">
          <div className="card-body">
            <h3 className="text-danger flex items-center gap-2 mb-2">
              <ShieldAlert size={18} /> Danger zone
            </h3>
            <p className="text-muted mb-4 text-sm">
              Deleting a workspace is irreversible. All projects, tasks, and members will be permanently removed. Proceed with caution.
            </p>
            <Button variant="danger" onClick={() => setShowDelete(true)}>
              Delete workspace
            </Button>
          </div>
        </div>
      )}

      <Modal
        isOpen={showDelete}
        onClose={() => {
          setShowDelete(false);
          setDeleteConfirmSlug('');
          setDeleteError('');
        }}
        title="Delete workspace"
        size="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setShowDelete(false)}>
              Cancel
            </Button>
            <Button variant="danger" onClick={handleDelete} isLoading={deleteLoading} disabled={deleteConfirmSlug !== currentTenant.slug}>
              Permanently delete
            </Button>
          </>
        }
      >
        <ErrorState message={deleteError} />
        <p className="mb-4">
          Are you sure you want to delete the workspace <strong>{currentTenant.name}</strong>? This action cannot be undone.
        </p>
        <div className="form-group mb-0">
          <label htmlFor="delete-confirm-slug">
            Please type <strong>{currentTenant.slug}</strong> to confirm.
          </label>
          <input
            id="delete-confirm-slug"
            type="text"
            value={deleteConfirmSlug}
            onChange={(e) => setDeleteConfirmSlug(e.target.value)}
            placeholder={currentTenant.slug}
            autoComplete="off"
          />
        </div>
      </Modal>
    </div>
  );
};

export default Settings;
