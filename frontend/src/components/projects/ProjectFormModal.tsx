import React, { useEffect, useState } from 'react';
import { apiError, projectApi, type Project, type ProjectStatus } from '../../api/work';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Select } from '../ui/Select';
import { Button } from '../ui/Button';
import { ErrorState } from '../ui/ErrorState';
import { PROJECT_STATUS_OPTIONS } from '../tasks/taskMeta';

interface ProjectFormModalProps {
  isOpen: boolean;
  onClose: () => void;
  tenantId: string;
  /** null = create. */
  project: Project | null;
  onSaved: (project: Project) => void;
}

export const ProjectFormModal = ({ isOpen, onClose, tenantId, project, onSaved }: ProjectFormModalProps) => {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState<ProjectStatus>('ACTIVE');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!isOpen) return;
    setName(project?.name ?? '');
    setDescription(project?.description ?? '');
    setStatus(project?.status ?? 'ACTIVE');
    setError('');
    setFieldErrors({});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, project?.id]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!name.trim()) {
      setFieldErrors({ name: 'Name is required' });
      return;
    }
    setFieldErrors({});
    setSaving(true);
    try {
      const input = { name: name.trim(), description, status };
      const saved = project ? await projectApi.update(tenantId, project.id, input) : await projectApi.create(tenantId, input);
      onSaved(saved);
      onClose();
    } catch (err) {
      const { message, fieldErrors: serverFieldErrors } = apiError(err, 'Failed to save project');
      setError(message);
      setFieldErrors(serverFieldErrors);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={project ? 'Edit project' : 'Create project'}>
      <form id="project-form" onSubmit={handleSubmit} noValidate>
        <ErrorState message={error} />
        <Input
          id="project-name"
          label="Name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={fieldErrors.name}
          maxLength={160}
          required
          placeholder="e.g. Website Redesign"
          autoFocus
        />
        <div className="form-group">
          <label htmlFor="project-description">Description</label>
          <textarea
            id="project-description"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={3}
            maxLength={2000}
            placeholder="Brief description of the project"
          />
          {fieldErrors.description && <div className="field-error">{fieldErrors.description}</div>}
        </div>
        <Select
          id="project-status"
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value as ProjectStatus)}
          options={PROJECT_STATUS_OPTIONS}
        />
      </form>
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose} disabled={saving}>
          Cancel
        </Button>
        <Button type="submit" form="project-form" isLoading={saving}>
          Save
        </Button>
      </div>
    </Modal>
  );
};
