import React, { useEffect, useState, useMemo } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import client from '../api/client';
import { useTenant } from '../contexts/TenantContext';
import type { Project } from './Projects';
import type { Member } from './Members';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Modal } from '../components/ui/Modal';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { Badge } from '../components/ui/Badge';
import { Table } from '../components/ui/Table';
import { TableSkeleton } from '../components/ui/Skeleton';
import { EmptyState } from '../components/ui/EmptyState';
import { ErrorState } from '../components/ui/ErrorState';
import { CheckSquare, Plus, Search, Edit2, Trash2, Filter } from 'lucide-react';

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

const Tasks = () => {
  const { currentTenant } = useTenant();
  const [searchParams, setSearchParams] = useSearchParams();
  
  const [projects, setProjects] = useState<Project[]>([]);
  const [members, setMembers] = useState<Member[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>(searchParams.get('projectId') || '');
  
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('');
  
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

  useEffect(() => {
    if (currentTenant) {
      fetchProjectsAndMembers();
    }
  }, [currentTenant]);

  useEffect(() => {
    if (selectedProjectId) {
      setSearchParams({ projectId: selectedProjectId });
      fetchTasks();
    } else {
      setTasks([]);
      setLoading(false);
    }
  }, [selectedProjectId, currentTenant]);

  const fetchProjectsAndMembers = async () => {
    if (!currentTenant) return;
    try {
      const [projRes, memRes] = await Promise.all([
        client.get(`/api/tenants/${currentTenant.id}/projects`),
        client.get(`/api/tenants/${currentTenant.id}/members`)
      ]);
      setProjects(projRes.data);
      setMembers(memRes.data);
      if (!selectedProjectId && projRes.data.length > 0) {
        setSelectedProjectId(projRes.data[0].id);
      } else if (projRes.data.length === 0) {
        setLoading(false);
      }
    } catch (err) {
      setError('Failed to load required data.');
      setLoading(false);
    }
  };

  const fetchTasks = async () => {
    if (!currentTenant || !selectedProjectId) return;
    setLoading(true);
    try {
      const res = await client.get(`/api/tenants/${currentTenant.id}/projects/${selectedProjectId}/tasks`);
      setTasks(res.data);
    } catch (err: any) {
      setError('Failed to load tasks');
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
      if (editingId) {
        await client.put(`/api/tenants/${currentTenant.id}/projects/${selectedProjectId}/tasks/${editingId}`, {
          title: formTitle,
          description: formDesc,
          status: formStatus,
          priority: formPriority,
          assigneeId: formAssigneeId || null
        });
      } else {
        await client.post(`/api/tenants/${currentTenant.id}/projects/${selectedProjectId}/tasks`, {
          title: formTitle,
          description: formDesc,
          status: formStatus,
          priority: formPriority,
          assigneeId: formAssigneeId || null
        });
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
    try {
      await client.delete(`/api/tenants/${currentTenant.id}/projects/${selectedProjectId}/tasks/${deleteId}`);
      setDeleteId(null);
      fetchTasks();
    } catch (err: any) {
      alert('Failed to delete task: ' + (err.response?.data?.message || err.message));
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
    setShowForm(true);
  };

  const openEdit = (task: Task) => {
    setEditingId(task.id);
    setFormTitle(task.title);
    setFormDesc(task.description || '');
    setFormStatus(task.status);
    setFormPriority(task.priority);
    setFormAssigneeId(task.assigneeId || '');
    setShowForm(true);
  };

  const handleStatusChange = async (task: Task, newStatus: string) => {
    if (!currentTenant || !selectedProjectId) return;
    try {
      await client.put(`/api/tenants/${currentTenant.id}/projects/${selectedProjectId}/tasks/${task.id}`, {
        title: task.title,
        description: task.description,
        status: newStatus,
        priority: task.priority,
        assigneeId: task.assigneeId
      });
      fetchTasks();
    } catch (err: any) {
      alert('Failed to update status');
    }
  };

  const filteredTasks = useMemo(() => {
    return tasks.filter(t => {
      const matchSearch = t.title.toLowerCase().includes(search.toLowerCase());
      const matchStatus = statusFilter ? t.status === statusFilter : true;
      return matchSearch && matchStatus;
    });
  }, [tasks, search, statusFilter]);

  if (!currentTenant) return null;

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="mb-1">Tasks</h1>
          <p className="text-muted text-sm">Manage work items across your projects.</p>
        </div>
        <div className="flex items-center gap-3">
          {projects.length > 0 && (
            <select 
              value={selectedProjectId} 
              onChange={e => setSelectedProjectId(e.target.value)}
              style={{ padding: '0.4375rem 0.875rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}
            >
              <option value="" disabled>Select Project...</option>
              {projects.map(p => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          )}
          {selectedProjectId && (
            <Button onClick={openCreate}><Plus size={16} /> Create Task</Button>
          )}
        </div>
      </div>

      <ErrorState message={error} />

      <Modal 
        isOpen={showForm} 
        onClose={() => setShowForm(false)} 
        title={editingId ? 'Edit Task' : 'Create Task'}
      >
        <form id="task-form" onSubmit={handleSubmit}>
          <ErrorState message={formError} />
          <Input 
            label="Title" 
            value={formTitle} 
            onChange={e => setFormTitle(e.target.value)} 
            required 
            placeholder="What needs to be done?"
          />
          <div className="form-group mb-4">
            <label>Description</label>
            <textarea 
              value={formDesc} 
              onChange={e => setFormDesc(e.target.value)} 
              rows={3}
              placeholder="Add more details..."
            />
          </div>
          <div className="grid grid-cols-2 gap-4">
            <Select 
              label="Status" 
              value={formStatus} 
              onChange={e => setFormStatus(e.target.value as any)}
              options={[
                { value: 'TODO', label: 'To Do' },
                { value: 'IN_PROGRESS', label: 'In Progress' },
                { value: 'DONE', label: 'Done' }
              ]}
            />
            <Select 
              label="Priority" 
              value={formPriority} 
              onChange={e => setFormPriority(e.target.value as any)}
              options={[
                { value: 'LOW', label: 'Low' },
                { value: 'MEDIUM', label: 'Medium' },
                { value: 'HIGH', label: 'High' }
              ]}
            />
            <Select 
              label="Assignee" 
              value={formAssigneeId} 
              onChange={e => setFormAssigneeId(e.target.value)}
              options={[
                { value: '', label: 'Unassigned' },
                ...members.map(m => ({ value: m.userId, label: m.name }))
              ]}
            />
          </div>
        </form>
        <div className="flex justify-end gap-2 mt-4 pt-4 border-t border-color" style={{ borderTop: '1px solid var(--border-color)' }}>
          <Button variant="ghost" onClick={() => setShowForm(false)}>Cancel</Button>
          <Button type="submit" form="task-form" isLoading={formLoading}>Save</Button>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!deleteId}
        title="Delete Task"
        message="Are you sure you want to delete this task? This action cannot be undone."
        confirmText="Delete Task"
        isDestructive
        isLoading={deleteLoading}
        onConfirm={handleDelete}
        onCancel={() => setDeleteId(null)}
      />

      <div className="flex gap-4 mb-4">
        <div className="relative flex-1 max-w-md">
          <Search size={18} className="text-muted absolute" style={{ top: '50%', transform: 'translateY(-50%)', left: '0.75rem' }} />
          <input 
            type="text" 
            placeholder="Search tasks..." 
            value={search}
            onChange={e => setSearch(e.target.value)}
            style={{ paddingLeft: '2.5rem' }}
          />
        </div>
        <div className="flex items-center gap-2">
          <Filter size={18} className="text-muted" />
          <select 
            value={statusFilter} 
            onChange={e => setStatusFilter(e.target.value)}
            style={{ padding: '0.4375rem 0.875rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}
          >
            <option value="">All Statuses</option>
            <option value="TODO">To Do</option>
            <option value="IN_PROGRESS">In Progress</option>
            <option value="DONE">Done</option>
          </select>
        </div>
      </div>

      {!selectedProjectId ? (
        <EmptyState 
          icon={<CheckSquare size={48} />}
          title="No project selected"
          description={projects.length > 0 ? "Select a project from the dropdown above to view tasks." : "Create a project first to start managing tasks."}
          action={projects.length === 0 ? <Link to={`/tenants/${currentTenant.id}/projects`} className="btn btn-primary">Go to Projects</Link> : undefined}
        />
      ) : loading ? (
        <TableSkeleton rows={5} cols={6} />
      ) : filteredTasks.length === 0 ? (
        <EmptyState 
          icon={<CheckSquare size={48} />}
          title="No tasks found"
          description={search || statusFilter ? "We couldn't find any tasks matching your filters." : "This project doesn't have any tasks yet."}
          action={(!search && !statusFilter) ? <Button onClick={openCreate}>Create Task</Button> : undefined}
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
            {filteredTasks.map(t => (
              <tr key={t.id}>
                <td>
                  <div className="font-medium text-main">{t.title}</div>
                  {t.description && <div className="text-xs text-muted mt-1 truncate max-w-xs">{t.description}</div>}
                </td>
                <td>
                  <select 
                    value={t.status} 
                    onChange={(e) => handleStatusChange(t, e.target.value)}
                    style={{ fontSize: '0.75rem', padding: '0.125rem 0.25rem', borderRadius: 'var(--radius-md)', border: '1px solid var(--border-color)' }}
                  >
                    <option value="TODO">TODO</option>
                    <option value="IN_PROGRESS">IN PROGRESS</option>
                    <option value="DONE">DONE</option>
                  </select>
                </td>
                <td>
                  <Badge color={t.priority === 'HIGH' ? 'red' : t.priority === 'MEDIUM' ? 'yellow' : 'blue'}>
                    {t.priority}
                  </Badge>
                </td>
                <td>
                  <span className="text-sm">
                    {t.assigneeId ? members.find(m => m.userId === t.assigneeId)?.name || 'Unknown' : <span className="text-muted">Unassigned</span>}
                  </span>
                </td>
                <td className="text-muted text-sm">{new Date(t.updatedAt).toLocaleDateString()}</td>
                <td>
                  <div className="flex items-center gap-1">
                    <Button variant="ghost" size="sm" onClick={() => openEdit(t)} title="Edit">
                      <Edit2 size={16} />
                    </Button>
                    {(currentTenant.myRole === 'ADMIN' || currentTenant.myRole === 'OWNER') && (
                      <Button variant="ghost" size="sm" onClick={() => setDeleteId(t.id)} title="Delete" className="text-danger">
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
