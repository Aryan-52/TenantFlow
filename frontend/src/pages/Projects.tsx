import React, { useEffect, useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import client from '../api/client';
import { useTenant } from '../contexts/TenantContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Modal } from '../components/ui/Modal';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { Table } from '../components/ui/Table';
import { TableSkeleton } from '../components/ui/Skeleton';
import { EmptyState } from '../components/ui/EmptyState';
import { ErrorState } from '../components/ui/ErrorState';
import { FolderKanban, Plus, Search, Edit2, Trash2 } from 'lucide-react';

export interface Project {
  id: string;
  name: string;
  description: string;
  createdAt: string;
  updatedAt: string;
}

const Projects = () => {
  const { currentTenant } = useTenant();
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  
  const [search, setSearch] = useState('');
  
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formError, setFormError] = useState('');
  const [formLoading, setFormLoading] = useState(false);

  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const canManage = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';

  useEffect(() => {
    fetchProjects();
  }, [currentTenant]);

  const fetchProjects = async () => {
    if (!currentTenant) return;
    setLoading(true);
    try {
      const res = await client.get(`/api/tenants/${currentTenant.id}/projects`);
      setProjects(res.data);
    } catch (err: any) {
      setError('Failed to load projects');
    } finally {
      setLoading(false);
    }
  };

  const filteredProjects = useMemo(() => {
    return projects.filter(p => p.name.toLowerCase().includes(search.toLowerCase()) || (p.description && p.description.toLowerCase().includes(search.toLowerCase())));
  }, [projects, search]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentTenant) return;
    setFormError('');
    setFormLoading(true);
    try {
      if (editingId) {
        await client.put(`/api/tenants/${currentTenant.id}/projects/${editingId}`, {
          name: formName,
          description: formDesc
        });
      } else {
        await client.post(`/api/tenants/${currentTenant.id}/projects`, {
          name: formName,
          description: formDesc
        });
      }
      setShowForm(false);
      fetchProjects();
    } catch (err: any) {
      setFormError(err.response?.data?.message || 'Failed to save project');
    } finally {
      setFormLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!currentTenant || !deleteId) return;
    setDeleteLoading(true);
    try {
      await client.delete(`/api/tenants/${currentTenant.id}/projects/${deleteId}`);
      setDeleteId(null);
      fetchProjects();
    } catch (err: any) {
      alert('Failed to delete project: ' + (err.response?.data?.message || err.message));
    } finally {
      setDeleteLoading(false);
    }
  };

  const openCreate = () => {
    setEditingId(null);
    setFormName('');
    setFormDesc('');
    setShowForm(true);
  };

  const openEdit = (project: Project) => {
    setEditingId(project.id);
    setFormName(project.name);
    setFormDesc(project.description || '');
    setShowForm(true);
  };

  if (!currentTenant) return null;

  return (
    <div>
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 className="mb-1">Projects</h1>
          <p className="text-muted text-sm">Manage workspaces for your team's initiatives.</p>
        </div>
        {canManage && (
          <Button onClick={openCreate}><Plus size={16} /> Create Project</Button>
        )}
      </div>

      <ErrorState message={error} />

      <Modal 
        isOpen={showForm} 
        onClose={() => setShowForm(false)} 
        title={editingId ? 'Edit Project' : 'Create Project'}
      >
        <form id="project-form" onSubmit={handleSubmit}>
          <ErrorState message={formError} />
          <Input 
            label="Name" 
            value={formName} 
            onChange={e => setFormName(e.target.value)} 
            required 
            placeholder="e.g. Website Redesign"
          />
          <div className="form-group mb-4">
            <label>Description</label>
            <textarea 
              value={formDesc} 
              onChange={e => setFormDesc(e.target.value)} 
              rows={3}
              placeholder="Brief description of the project"
            />
          </div>
        </form>
        <div className="flex justify-end gap-2 mt-4 pt-4 border-t border-color" style={{ borderTop: '1px solid var(--border-color)' }}>
          <Button variant="ghost" onClick={() => setShowForm(false)}>Cancel</Button>
          <Button type="submit" form="project-form" isLoading={formLoading}>Save</Button>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!deleteId}
        title="Delete Project"
        message="Are you sure you want to delete this project? This action cannot be undone and will delete all associated tasks."
        confirmText="Delete Project"
        isDestructive
        isLoading={deleteLoading}
        onConfirm={handleDelete}
        onCancel={() => setDeleteId(null)}
      />

      <div className="mb-4 relative max-w-md">
        <Search size={18} className="text-muted absolute" style={{ top: '50%', transform: 'translateY(-50%)', left: '0.75rem' }} />
        <input 
          type="text" 
          placeholder="Search projects..." 
          value={search}
          onChange={e => setSearch(e.target.value)}
          style={{ paddingLeft: '2.5rem' }}
        />
      </div>

      {loading ? (
        <TableSkeleton rows={4} cols={4} />
      ) : filteredProjects.length === 0 ? (
        <EmptyState 
          icon={<FolderKanban size={48} />}
          title="No projects found"
          description={search ? "We couldn't find any projects matching your search." : "Get started by creating your first project."}
          action={!search && canManage ? <Button onClick={openCreate}>Create Project</Button> : undefined}
        />
      ) : (
        <Table>
          <thead>
            <tr>
              <th>Name</th>
              <th>Description</th>
              <th>Created</th>
              <th style={{ width: '120px' }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredProjects.map(p => (
              <tr key={p.id}>
                <td>
                  <Link to={`/tenants/${currentTenant.id}/projects/${p.id}`} className="font-medium" style={{ color: 'var(--primary-color)', textDecoration: 'none' }}>
                    {p.name}
                  </Link>
                </td>
                <td className="text-muted" style={{ maxWidth: '300px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {p.description || '-'}
                </td>
                <td className="text-muted text-sm">{new Date(p.createdAt).toLocaleDateString()}</td>
                <td>
                  <div className="flex items-center gap-1">
                    {canManage && (
                      <>
                        <Button variant="ghost" size="sm" onClick={() => openEdit(p)} title="Edit">
                          <Edit2 size={16} />
                        </Button>
                        <Button variant="ghost" size="sm" onClick={() => setDeleteId(p.id)} title="Delete" className="text-danger">
                          <Trash2 size={16} />
                        </Button>
                      </>
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

export default Projects;
