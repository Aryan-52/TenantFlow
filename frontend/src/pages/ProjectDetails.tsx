import React, { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import client from '../api/client';
import { useTenant } from '../contexts/TenantContext';
import type { Project } from './Projects';
import type { Task } from './Tasks';
import type { Member } from './Members';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { Modal } from '../components/ui/Modal';
import { ErrorState } from '../components/ui/ErrorState';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { StatusBadge, PriorityBadge } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { EmptyState } from '../components/ui/EmptyState';
import { ArrowLeft, Trash2, Edit2, ExternalLink, Plus, CheckSquare } from 'lucide-react';

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

const ProjectDetails = () => {
  const { projectId } = useParams();
  const { currentTenant } = useTenant();
  const navigate = useNavigate();

  const [project, setProject] = useState<Project | null>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [members, setMembers] = useState<Member[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');

  const [deleteProjectOpen, setDeleteProjectOpen] = useState(false);
  const [deleteProjectLoading, setDeleteProjectLoading] = useState(false);

  const [showTaskForm, setShowTaskForm] = useState(false);
  const [editingTaskId, setEditingTaskId] = useState<string | null>(null);
  const [formTitle, setFormTitle] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formStatus, setFormStatus] = useState<'TODO' | 'IN_PROGRESS' | 'DONE'>('TODO');
  const [formPriority, setFormPriority] = useState<'LOW' | 'MEDIUM' | 'HIGH'>('MEDIUM');
  const [formAssigneeId, setFormAssigneeId] = useState('');
  const [formError, setFormError] = useState('');
  const [formLoading, setFormLoading] = useState(false);

  const [deleteTaskId, setDeleteTaskId] = useState<string | null>(null);
  const [deleteTaskLoading, setDeleteTaskLoading] = useState(false);

  const canManage = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';

  useEffect(() => {
    if (currentTenant && projectId) {
      fetchProjectData();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentTenant?.id, projectId]);

  const fetchProjectData = async () => {
    setLoading(true);
    setError('');
    try {
      const [projRes, tasksRes, membersRes] = await Promise.all([
        client.get(`/api/tenants/${currentTenant!.id}/projects/${projectId}`),
        client.get(`/api/tenants/${currentTenant!.id}/projects/${projectId}/tasks`),
        client.get(`/api/tenants/${currentTenant!.id}/members`),
      ]);
      setProject(projRes.data);
      setTasks(tasksRes.data);
      setMembers(membersRes.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load project details');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteProject = async () => {
    if (!currentTenant || !project) return;
    setDeleteProjectLoading(true);
    try {
      await client.delete(`/api/tenants/${currentTenant.id}/projects/${project.id}`);
      navigate(`/tenants/${currentTenant.id}/projects`);
    } catch (err: any) {
      setActionError(err.response?.data?.message || 'Failed to delete project');
      setDeleteProjectLoading(false);
      setDeleteProjectOpen(false);
    }
  };

  const openCreateTask = () => {
    setEditingTaskId(null);
    setFormTitle('');
    setFormDesc('');
    setFormStatus('TODO');
    setFormPriority('MEDIUM');
    setFormAssigneeId('');
    setFormError('');
    setShowTaskForm(true);
  };

  const openEditTask = (task: Task) => {
    setEditingTaskId(task.id);
    setFormTitle(task.title);
    setFormDesc(task.description || '');
    setFormStatus(task.status);
    setFormPriority(task.priority);
    setFormAssigneeId(task.assigneeId || '');
    setFormError('');
    setShowTaskForm(true);
  };

  const handleTaskSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentTenant || !project) return;
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
      if (editingTaskId) {
        await client.put(`/api/tenants/${currentTenant.id}/projects/${project.id}/tasks/${editingTaskId}`, payload);
      } else {
        await client.post(`/api/tenants/${currentTenant.id}/projects/${project.id}/tasks`, payload);
      }
      setShowTaskForm(false);
      fetchProjectData();
    } catch (err: any) {
      setFormError(err.response?.data?.message || 'Failed to save task');
    } finally {
      setFormLoading(false);
    }
  };

  const handleDeleteTask = async () => {
    if (!currentTenant || !project || !deleteTaskId) return;
    setDeleteTaskLoading(true);
    try {
      await client.delete(`/api/tenants/${currentTenant.id}/projects/${project.id}/tasks/${deleteTaskId}`);
      setDeleteTaskId(null);
      fetchProjectData();
    } catch (err: any) {
      setActionError(err.response?.data?.message || 'Failed to delete task');
    } finally {
      setDeleteTaskLoading(false);
    }
  };

  if (loading) {
    return (
      <div className="flex flex-col gap-6">
        <Skeleton style={{ height: '3rem', width: '30%' }} />
        <Skeleton style={{ height: '8rem' }} />
        <Skeleton style={{ height: '12rem' }} />
      </div>
    );
  }

  if (error) return <ErrorState message={error} />;
  if (!project) return <ErrorState message="Project not found" />;

  const todoCount = tasks.filter((t) => t.status === 'TODO').length;
  const inProgressCount = tasks.filter((t) => t.status === 'IN_PROGRESS').length;
  const doneCount = tasks.filter((t) => t.status === 'DONE').length;

  return (
    <div>
      <div className="mb-4">
        <Link to={`/tenants/${currentTenant?.id}/projects`} className="text-muted flex items-center gap-1 text-sm" style={{ textDecoration: 'none', width: 'fit-content' }}>
          <ArrowLeft size={16} /> Back to projects
        </Link>
      </div>

      <div className="flex justify-between items-start gap-4 mb-6 flex-col sm:flex-row">
        <div>
          <h1 className="mb-2">{project.name}</h1>
          <p className="text-muted max-w-2xl">{project.description || 'No description provided.'}</p>
        </div>
        {canManage && (
          <Button variant="danger-outline" onClick={() => setDeleteProjectOpen(true)}>
            <Trash2 size={16} /> Delete project
          </Button>
        )}
      </div>

      <ErrorState message={actionError} />

      <ConfirmDialog
        isOpen={deleteProjectOpen}
        title="Delete project"
        message="Are you sure you want to delete this project? All associated tasks will be lost."
        confirmText="Delete project"
        isDestructive
        isLoading={deleteProjectLoading}
        onConfirm={handleDeleteProject}
        onCancel={() => setDeleteProjectOpen(false)}
      />

      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
        <div className="card card-padded text-center">
          <p className="text-muted text-sm font-medium mb-1">Total tasks</p>
          <h2 className="mb-0">{tasks.length}</h2>
        </div>
        <div className="card card-padded text-center">
          <p className="text-muted text-sm font-medium mb-1">To do</p>
          <h2 className="mb-0">{todoCount}</h2>
        </div>
        <div className="card card-padded text-center" style={{ backgroundColor: 'var(--primary-light)', borderColor: 'var(--primary-100)' }}>
          <p className="text-sm font-medium mb-1 text-primary">In progress</p>
          <h2 className="mb-0 text-primary">{inProgressCount}</h2>
        </div>
        <div className="card card-padded text-center" style={{ backgroundColor: 'var(--success-bg)', borderColor: 'var(--success-border)' }}>
          <p className="text-sm font-medium mb-1 text-success">Done</p>
          <h2 className="mb-0 text-success">{doneCount}</h2>
        </div>
      </div>

      <Modal isOpen={showTaskForm} onClose={() => setShowTaskForm(false)} title={editingTaskId ? 'Edit task' : 'Create task'}>
        <form id="project-task-form" onSubmit={handleTaskSubmit} noValidate>
          <ErrorState message={formError} />
          <Input label="Title" value={formTitle} onChange={(e) => setFormTitle(e.target.value)} required placeholder="What needs to be done?" autoFocus />
          <div className="form-group">
            <label htmlFor="pd-task-description">Description</label>
            <textarea id="pd-task-description" value={formDesc} onChange={(e) => setFormDesc(e.target.value)} rows={3} placeholder="Add more details..." />
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
          <Button variant="secondary" onClick={() => setShowTaskForm(false)}>
            Cancel
          </Button>
          <Button type="submit" form="project-task-form" isLoading={formLoading}>
            Save
          </Button>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!deleteTaskId}
        title="Delete task"
        message="Are you sure you want to delete this task? This action cannot be undone."
        confirmText="Delete task"
        isDestructive
        isLoading={deleteTaskLoading}
        onConfirm={handleDeleteTask}
        onCancel={() => setDeleteTaskId(null)}
      />

      <div className="card">
        <div className="card-header">
          <h3 className="mb-0">Tasks</h3>
          <div className="flex gap-2">
            <Button size="sm" onClick={openCreateTask}>
              <Plus size={14} /> Add task
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={() => navigate(`/tenants/${currentTenant?.id}/tasks?projectId=${project.id}`)}
            >
              Full task board <ExternalLink size={14} />
            </Button>
          </div>
        </div>

        <div className="card-body">
          {tasks.length === 0 ? (
            <EmptyState icon={<CheckSquare size={22} />} title="No tasks yet" description="Add the first task to get this project moving." />
          ) : (
            <div className="flex flex-col gap-2">
              {tasks.slice(0, 8).map((t) => (
                <div key={t.id} className="flex justify-between items-center gap-3 p-3 border rounded-md">
                  <div style={{ minWidth: 0 }}>
                    <div className="font-medium truncate">{t.title}</div>
                    <div className="flex items-center gap-2 mt-1">
                      <StatusBadge status={t.status} />
                      <PriorityBadge priority={t.priority} />
                    </div>
                  </div>
                  <div className="flex items-center gap-1 flex-shrink-0">
                    <Button variant="ghost" size="sm" className="btn-icon" onClick={() => openEditTask(t)} title="Edit">
                      <Edit2 size={15} />
                    </Button>
                    <Button variant="ghost" size="sm" className="btn-icon text-danger" onClick={() => setDeleteTaskId(t.id)} title="Delete">
                      <Trash2 size={15} />
                    </Button>
                  </div>
                </div>
              ))}
              {tasks.length > 8 && (
                <p className="text-sm text-muted text-center mt-2">
                  +{tasks.length - 8} more task{tasks.length - 8 === 1 ? '' : 's'} — see the full task board.
                </p>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ProjectDetails;
