import React, { useEffect, useState, useMemo } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import client from '../api/client';
import { useTenant } from '../contexts/TenantContext';
import type { Project } from './Projects';
import type { Member } from './Members';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { SearchInput } from '../components/ui/SearchInput';
import { Modal } from '../components/ui/Modal';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { PriorityBadge } from '../components/ui/Badge';
import { Table } from '../components/ui/Table';
import { TableSkeleton } from '../components/ui/Skeleton';
import { EmptyState } from '../components/ui/EmptyState';
import { ErrorState } from '../components/ui/ErrorState';
import { PageHeader } from '../components/ui/PageHeader';
import { CheckSquare, Plus, Edit2, Trash2 } from 'lucide-react';

export interface Task {
  id: string;
  projectId: string;
  title: string;
  description: string;
  status: 'TODO' | 'IN_PROGRESS' | 'DONE';
  priority: 'LOW' | 'MEDIUM' | 'HIGH';
  assigneeId: string | null;
  createdAt: string;
  updatedAt: string;
}

const STATUS_OPTIONS = [
  { value: 'TODO', label: 'To Do' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'DONE', label: 'Done' },
];
const PRIORITY_OPTIONS = [
  { value: 'LOW', label: 'Low' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' },
];

const Tasks = () => {
  const { currentTenant } = useTenant();
  const [searchParams, setSearchParams] = useSearchParams();

  const [projects, setProjects] = useState<Project[]>([]);
  const [members, setMembers] = useState<Member[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>(searchParams.get('projectId') || '');

  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');

  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  const [priorityFilter, setPriorityFilter] = useState('');
  const [assigneeFilter, setAssigneeFilter] = useState('');

  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formTitle, setFormTitle] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formStatus, setFormStatus] = useState<'TODO' | 'IN_PROGRESS' | 'DONE'>('TODO');
  const [formPriority, setFormPriority] = useState<'LOW' | 'MEDIUM' | 'HIGH'>('MEDIUM');
  const [formAssigneeId, setFormAssigneeId] = useState<string>('');
  const [formError, setFormError] = useState('');
  const [formLoading, setFormLoading] = useState(false);

  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const canDelete = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';

  useEffect(() => {
    if (currentTenant) {
      fetchProjectsAndMembers();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentTenant?.id]);

  useEffect(() => {
    if (selectedProjectId) {
      setSearchParams({ projectId: selectedProjectId });
      fetchTasks();
    } else {
      setTasks([]);
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProjectId, currentTenant?.id]);

  const fetchProjectsAndMembers = async () => {
    if (!currentTenant) return;
    try {
      const [projRes, memRes] = await Promise.all([
        client.get(`/api/tenants/${currentTenant.id}/projects`),
        client.get(`/api/tenants/${currentTenant.id}/members`),
      ]);
      setProjects(projRes.data);
      setMembers(memRes.data);
      if (!selectedProjectId && projRes.data.length > 0) {
        setSelectedProjectId(projRes.data[0].id);
      } else if (projRes.data.length === 0) {
        setLoading(false);
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load required data.');
      setLoading(false);
    }
  };

  const fetchTasks = async () => {
    if (!currentTenant || !selectedProjectId) return;
    setLoading(true);
    setError('');
    try {
      const res = await client.get(`/api/tenants/${currentTenant.id}/projects/${selectedProjectId}/tasks`);
      setTasks(res.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load tasks');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentTenant || !selectedProjectId) return;
    setFormError('');
    setFormLoading(true);
    try {
      const payload = {
        title: formTitle,
        description: formDesc,
        status: formStatus,
        priority: formPriority,
        assigneeId: formAssigneeId || null,
      };
      if (editingId) {
        await client.put(`/api/tenants/${currentTenant.id}/projects/${selectedProjectId}/tasks/${editingId}`, payload);
      } else {
        await client.post(`/api/tenants/${currentTenant.id}/projects/${selectedProjectId}/tasks`, payload);
      }
      setShowForm(false);
      fetchTasks();
    } catch (err: any) {
      setFormError(err.response?.data?.message || 'Failed to save task');
    } finally {
      setFormLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!currentTenant || !selectedProjectId || !deleteId) return;
    setDeleteLoading(true);
    setActionError('');
    try {
      await client.delete(`/api/tenants/${currentTenant.id}/projects/${selectedProjectId}/tasks/${deleteId}`);
      setDeleteId(null);
      fetchTasks();
    } catch (err: any) {
      setActionError(err.response?.data?.message || 'Failed to delete task');
    } finally {
      setDeleteLoading(false);
    }
  };

  const openCreate = () => {
    setEditingId(null);
    setFormTitle('');
    setFormDesc('');
    setFormStatus('TODO');
    setFormPriority('MEDIUM');
    setFormAssigneeId('');
    setFormError('');
    setShowForm(true);
  };

  const openEdit = (task: Task) => {
    setEditingId(task.id);
    setFormTitle(task.title);
    setFormDesc(task.description || '');
    setFormStatus(task.status);
    setFormPriority(task.priority);
    setFormAssigneeId(task.assigneeId || '');
    setFormError('');
    setShowForm(true);
  };

  const handleStatusChange = async (task: Task, newStatus: string) => {
    if (!currentTenant || !selectedProjectId) return;
    setActionError('');
    try {
      await client.put(`/api/tenants/${currentTenant.id}/projects/${selectedProjectId}/tasks/${task.id}`, {
        title: task.title,
        description: task.description,
        status: newStatus,
        priority: task.priority,
        assigneeId: task.assigneeId,
      });
      fetchTasks();
    } catch (err: any) {
      setActionError(err.response?.data?.message || 'Failed to update task status');
    }
  };

  const filteredTasks = useMemo(() => {
    return tasks.filter((t) => {
      const matchSearch = t.title.toLowerCase().includes(search.toLowerCase());
      const matchStatus = statusFilter ? t.status === statusFilter : true;
      const matchPriority = priorityFilter ? t.priority === priorityFilter : true;
      const matchAssignee = assigneeFilter
        ? assigneeFilter === 'unassigned'
          ? !t.assigneeId
          : t.assigneeId === assigneeFilter
        : true;
      return matchSearch && matchStatus && matchPriority && matchAssignee;
    });
  }, [tasks, search, statusFilter, priorityFilter, assigneeFilter]);

  const hasActiveFilters = !!(search || statusFilter || priorityFilter || assigneeFilter);

  if (!currentTenant) return null;

  return (
    <div>
      <PageHeader
        title="Tasks"
        subtitle="Manage work items across your projects."
        actions={
          <div className="flex items-center gap-2 flex-wrap">
            {projects.length > 0 && (
              <Select
                value={selectedProjectId}
                onChange={(e) => setSelectedProjectId(e.target.value)}
                options={projects.map((p) => ({ value: p.id, label: p.name }))}
                aria-label="Select project"
                style={{ minWidth: '180px' }}
              />
            )}
            {selectedProjectId && (
              <Button onClick={openCreate}>
                <Plus size={16} /> Create task
              </Button>
            )}
          </div>
        }
      />

      <ErrorState message={error} />
      <ErrorState message={actionError} />

      <Modal isOpen={showForm} onClose={() => setShowForm(false)} title={editingId ? 'Edit task' : 'Create task'}>
        <form id="task-form" onSubmit={handleSubmit} noValidate>
          <ErrorState message={formError} />
          <Input
            label="Title"
            value={formTitle}
            onChange={(e) => setFormTitle(e.target.value)}
            required
            placeholder="What needs to be done?"
            autoFocus
          />
          <div className="form-group">
            <label htmlFor="task-description">Description</label>
            <textarea
              id="task-description"
              value={formDesc}
              onChange={(e) => setFormDesc(e.target.value)}
              rows={3}
              placeholder="Add more details..."
            />
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Select label="Status" value={formStatus} onChange={(e) => setFormStatus(e.target.value as any)} options={STATUS_OPTIONS} />
            <Select label="Priority" value={formPriority} onChange={(e) => setFormPriority(e.target.value as any)} options={PRIORITY_OPTIONS} />
            <div className="md:col-span-2">
              <Select
                label="Assignee"
                value={formAssigneeId}
                onChange={(e) => setFormAssigneeId(e.target.value)}
                options={[{ value: '', label: 'Unassigned' }, ...members.map((m) => ({ value: m.userId, label: m.name }))]}
              />
            </div>
          </div>
        </form>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setShowForm(false)}>
            Cancel
          </Button>
          <Button type="submit" form="task-form" isLoading={formLoading}>
            Save
          </Button>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!deleteId}
        title="Delete task"
        message="Are you sure you want to delete this task? This action cannot be undone."
        confirmText="Delete task"
        isDestructive
        isLoading={deleteLoading}
        onConfirm={handleDelete}
        onCancel={() => setDeleteId(null)}
      />

      {selectedProjectId && (
        <div className="flex flex-wrap gap-3 mb-4">
          <SearchInput value={search} onChange={setSearch} placeholder="Search tasks..." className="max-w-md" style={{ flex: '1 1 200px' }} />
          <Select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            options={[{ value: '', label: 'All statuses' }, ...STATUS_OPTIONS]}
            aria-label="Filter by status"
            style={{ maxWidth: '160px' }}
          />
          <Select
            value={priorityFilter}
            onChange={(e) => setPriorityFilter(e.target.value)}
            options={[{ value: '', label: 'All priorities' }, ...PRIORITY_OPTIONS]}
            aria-label="Filter by priority"
            style={{ maxWidth: '160px' }}
          />
          <Select
            value={assigneeFilter}
            onChange={(e) => setAssigneeFilter(e.target.value)}
            options={[
              { value: '', label: 'All assignees' },
              { value: 'unassigned', label: 'Unassigned' },
              ...members.map((m) => ({ value: m.userId, label: m.name })),
            ]}
            aria-label="Filter by assignee"
            style={{ maxWidth: '180px' }}
          />
        </div>
      )}

      {!selectedProjectId ? (
        <EmptyState
          icon={<CheckSquare size={24} />}
          title="No project selected"
          description={projects.length > 0 ? 'Select a project from the dropdown above to view tasks.' : 'Create a project first to start managing tasks.'}
          action={projects.length === 0 ? <Link to={`/tenants/${currentTenant.id}/projects`} className="btn btn-primary">Go to projects</Link> : undefined}
        />
      ) : loading ? (
        <TableSkeleton rows={5} cols={6} />
      ) : filteredTasks.length === 0 ? (
        <EmptyState
          icon={<CheckSquare size={24} />}
          title="No tasks found"
          description={hasActiveFilters ? "We couldn't find any tasks matching your filters." : "This project doesn't have any tasks yet."}
          action={!hasActiveFilters ? <Button onClick={openCreate}>Create task</Button> : undefined}
        />
      ) : (
        <Table>
          <thead>
            <tr>
              <th>Task</th>
              <th>Status</th>
              <th>Priority</th>
              <th>Assignee</th>
              <th>Updated</th>
              <th style={{ width: '120px' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredTasks.map((t) => (
              <tr key={t.id}>
                <td>
                  <div className="font-medium text-main">{t.title}</div>
                  {t.description && <div className="text-xs text-muted mt-1 truncate max-w-xs">{t.description}</div>}
                </td>
                <td>
                  <select value={t.status} onChange={(e) => handleStatusChange(t, e.target.value)} style={{ fontSize: 'var(--text-xs)', padding: '0.25rem 1.5rem 0.25rem 0.5rem' }}>
                    {STATUS_OPTIONS.map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <PriorityBadge priority={t.priority} />
                </td>
                <td>
                  <span className="text-sm">
                    {t.assigneeId ? members.find((m) => m.userId === t.assigneeId)?.name || 'Unknown' : <span className="text-muted">Unassigned</span>}
                  </span>
                </td>
                <td className="text-muted text-sm">{new Date(t.updatedAt).toLocaleDateString()}</td>
                <td>
                  <div className="flex items-center gap-1">
                    <Button variant="ghost" size="sm" className="btn-icon" onClick={() => openEdit(t)} title="Edit">
                      <Edit2 size={16} />
                    </Button>
                    {canDelete && (
                      <Button variant="ghost" size="sm" className="btn-icon text-danger" onClick={() => setDeleteId(t.id)} title="Delete">
                        <Trash2 size={16} />
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </Table>
      )}
    </div>
  );
};

export default Tasks;
