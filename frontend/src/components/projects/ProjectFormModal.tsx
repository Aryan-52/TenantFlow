import React, { useEffect, useState } from 'react';
import { apiError, newIdempotencyKey, projectApi, type Project, type ProjectStatus } from '../../api/work';
import { useAuth } from '../../contexts/AuthContext';
import { useToast } from '../../contexts/ToastContext';
import type { Member } from '../../pages/Members';
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
  /** Members of the current workspace: the only eligible owners. */
  members: Member[];
  onSaved: (project: Project) => void;
}

export const ProjectFormModal = ({ isOpen, onClose, tenantId, project, members, onSaved }: ProjectFormModalProps) => {
  const { user } = useAuth();
  const toast = useToast();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState<ProjectStatus>('ACTIVE');
  const [ownerId, setOwnerId] = useState('');
  const [startDate, setStartDate] = useState('');
  const [targetDate, setTargetDate] = useState('');
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [idempotencyKey, setIdempotencyKey] = useState(newIdempotencyKey);

  useEffect(() => {
    if (!isOpen) return;
    setName(project?.name ?? '');
    setDescription(project?.description ?? '');
    setStatus(project?.status ?? 'ACTIVE');
    setStartDate(project?.startDate ?? '');
    setTargetDate(project?.targetDate ?? '');
    // New projects default to the person creating them; edits start from the current owner.
    setOwnerId(project ? project.ownerId ?? '' : user?.id ?? '');
    setError('');
    setFieldErrors({});
    setIdempotencyKey(newIdempotencyKey());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, project?.id]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (!name.trim()) {
      setFieldErrors({ name: 'Name is required' });
      return;
    }
    if (startDate && targetDate && targetDate < startDate) {
      setFieldErrors({ targetDate: 'The target date must be on or after the start date' });
      return;
    }
    setFieldErrors({});
    setSaving(true);
    try {
      const input = { name: name.trim(), description, status, ownerId: ownerId || null, startDate: startDate || null, targetDate: targetDate || null };
      const saved = project
        ? await projectApi.update(tenantId, project.id, { ...input, version: project.version })
        : await projectApi.create(tenantId, input, idempotencyKey);
      onSaved(saved);
      toast.success(project ? 'Project updated' : `Project "${saved.name}" created`);
      onClose();
    } catch (err) {
      const { message, fieldErrors: serverFieldErrors } = apiError(err, 'Failed to save project');
      setError(message);
      setFieldErrors(serverFieldErrors);
    } finally {
      setSaving(false);
    }
  };

  // Only current workspace members are eligible (the backend enforces this too). A selected
  // owner missing from the list - members still loading or failed to load - stays visible
  // and selectable, so the dropdown always shows what will actually be saved.
  const ownerOptions = [{ value: '', label: 'Unassigned' }, ...members.map((m) => ({ value: m.userId, label: m.name }))];
  if (ownerId && !ownerOptions.some((o) => o.value === ownerId)) {
    const label = ownerId === project?.ownerId ? project.ownerName : ownerId === user?.id ? user.name : null;
    ownerOptions.push({ value: ownerId, label: label ?? 'Current owner' });
  }

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
        <div className="form-row">
        <Select
          id="project-owner"
          label="Owner"
          value={ownerId}
          onChange={(e) => setOwnerId(e.target.value)}
          options={ownerOptions}
          error={fieldErrors.ownerId}
          hint="The workspace member who leads this project."
        />
        <Select
          id="project-status"
          label="Status"
          value={status}
          onChange={(e) => setStatus(e.target.value as ProjectStatus)}
          options={PROJECT_STATUS_OPTIONS}
        />
        </div>
        <div className="form-row">
          <Input id="project-start" type="date" label="Start date" value={startDate} max={targetDate || undefined}
            onChange={(e) => setStartDate(e.target.value)} error={fieldErrors.startDate} hint="Optional" />
          <Input id="project-target" type="date" label="Target date" value={targetDate} min={startDate || undefined}
            onChange={(e) => setTargetDate(e.target.value)} error={fieldErrors.targetDate} hint="Optional. Used for health and the timeline." />
        </div>
      </form>
      <div className="modal-actions">
        <Button variant="secondary" onClick={onClose} disabled={saving}>
          Cancel
        </Button>
        <Button type="submit" form="project-form" isLoading={saving}>
          {project ? 'Save changes' : 'Create project'}
        </Button>
      </div>
    </Modal>
  );
};
