import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import client from '../api/client';
import { useTenant } from '../contexts/TenantContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Badge } from '../components/ui/Badge';
import { ErrorState } from '../components/ui/ErrorState';
import { Modal } from '../components/ui/Modal';
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
      <div className="flex items-center gap-2 mb-6">
        <SettingsIcon size={24} className="text-muted" />
        <h1 className="mb-0">Workspace Settings</h1>
      </div>

      <div className="card mb-6">
        <h3 className="mb-4 border-b pb-2">General Information</h3>
        
        <div className="flex gap-4 mb-6">
          <div className="flex-1">
            <p className="text-muted text-sm font-medium mb-1">Your Role</p>
            <Badge color={currentTenant.myRole === 'OWNER' ? 'red' : 'yellow'}>{currentTenant.myRole}</Badge>
          </div>
          <div className="flex-1">
            <p className="text-muted text-sm font-medium mb-1">Workspace Slug</p>
            <code style={{ padding: '0.25rem 0.5rem', backgroundColor: 'var(--bg-subtle)', borderRadius: 'var(--radius-md)' }}>{currentTenant.slug}</code>
          </div>
        </div>

        <ErrorState message={error} />
        {success && (
          <div style={{ backgroundColor: '#d1e7dd', border: '1px solid #a3cfbb', color: '#0f5132', padding: '0.75rem 1rem', borderRadius: '0.25rem', marginBottom: '1rem' }}>
            {success}
          </div>
        )}

        <form onSubmit={handleUpdate}>
          <Input 
            label="Workspace Name" 
            value={name} 
            onChange={e => setName(e.target.value)} 
            required 
            placeholder="e.g. Acme Corp"
          />
          <Button type="submit" isLoading={loading}>Save Changes</Button>
        </form>
      </div>

      {isOwner && (
        <div className="card border-danger" style={{ borderColor: 'var(--danger-border)' }}>
          <h3 className="text-danger flex items-center gap-2 mb-2"><ShieldAlert size={18} /> Danger Zone</h3>
          <p className="text-muted mb-4 text-sm">
            Deleting a workspace is irreversible. All projects, tasks, and members will be permanently removed. 
            Proceed with caution.
          </p>
          <Button variant="danger" onClick={() => setShowDelete(true)}>Delete Workspace</Button>
        </div>
      )}

      <Modal
        isOpen={showDelete}
        onClose={() => { setShowDelete(false); setDeleteConfirmSlug(''); setDeleteError(''); }}
        title="Delete Workspace"
        footer={
          <>
            <Button variant="ghost" onClick={() => setShowDelete(false)}>Cancel</Button>
            <Button variant="danger" onClick={handleDelete} isLoading={deleteLoading} disabled={deleteConfirmSlug !== currentTenant.slug}>
              Permanently Delete
            </Button>
          </>
        }
      >
        <ErrorState message={deleteError} />
        <p className="mb-4">
          Are you sure you want to delete the workspace <strong>{currentTenant.name}</strong>? 
          This action cannot be undone.
        </p>
        <div className="form-group mb-0">
          <label>Please type <strong>{currentTenant.slug}</strong> to confirm.</label>
          <input 
            type="text" 
            value={deleteConfirmSlug} 
            onChange={e => setDeleteConfirmSlug(e.target.value)} 
            placeholder={currentTenant.slug}
          />
        </div>
      </Modal>
    </div>
  );
};

export default Settings;
