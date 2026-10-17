import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import client from '../../api/client';
import { useTenant } from '../../contexts/TenantContext';
import { useToast } from '../../contexts/ToastContext';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { ErrorState } from '../ui/ErrorState';

/** Create a workspace and switch to it. Used by the workspace switcher and the first-run screen. */
export const CreateWorkspaceModal = ({ isOpen, onClose }: { isOpen: boolean; onClose: () => void }) => {
  const { addTenant } = useTenant();
  const toast = useToast();
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [slug, setSlug] = useState('');
  const [slugTouched, setSlugTouched] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  // Suggest a slug from the name until the user edits it themselves.
  const suggested = name
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 50);
  const slugValue = slugTouched ? slug : suggested;

  const close = () => {
    setName('');
    setSlug('');
    setSlugTouched(false);
    setError('');
    onClose();
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await client.post('/api/tenants', { name, slug: slugValue });
      const created = res.data;
      addTenant({ id: created.id, name: created.name, slug: created.slug, myRole: created.myRole });
      toast.success(`Workspace "${created.name}" created`);
      close();
      navigate(`/tenants/${created.id}/dashboard`);
    } catch (err: any) {
      const fieldErrors = err.response?.data?.fieldErrors as Record<string, string> | undefined;
      setError(
        fieldErrors && Object.keys(fieldErrors).length > 0
          ? Object.values(fieldErrors).join('; ')
          : err.response?.data?.message || 'Failed to create workspace',
      );
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={close} title="Create new workspace">
      <form id="create-ws-form" onSubmit={handleCreate} noValidate>
        <ErrorState message={error} />
        <Input label="Workspace name" value={name} onChange={(e) => setName(e.target.value)} required placeholder="Acme Corp" autoComplete="off" autoFocus />
        <Input
          label="URL slug"
          value={slugValue}
          onChange={(e) => {
            setSlugTouched(true);
            setSlug(e.target.value);
          }}
          required
          pattern="^[a-z0-9]+(?:-[a-z0-9]+)*$"
          placeholder="acme-corp"
          autoComplete="off"
          hint='Lowercase letters, numbers and hyphens, e.g. "acme-corp".'
        />
      </form>
      <div className="modal-actions">
        <Button variant="secondary" onClick={close} type="button">
          Cancel
        </Button>
        <Button type="submit" form="create-ws-form" isLoading={loading} disabled={loading || !name.trim() || !slugValue}>
          Create workspace
        </Button>
      </div>
    </Modal>
  );
};
