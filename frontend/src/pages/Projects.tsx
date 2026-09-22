import React, { useEffect, useState, useMemo } from 'react';
import { Link } from 'react-router-dom';
import client from '../api/client';
import { useTenant } from '../contexts/TenantContext';
import { Button } from '../components/ui/Button';
import { Input } from '../components/ui/Input';
import { Select } from '../components/ui/Select';
import { SearchInput } from '../components/ui/SearchInput';
import { Modal } from '../components/ui/Modal';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { Table } from '../components/ui/Table';
import { TableSkeleton } from '../components/ui/Skeleton';
import { EmptyState } from '../components/ui/EmptyState';
import { ErrorState } from '../components/ui/ErrorState';
import { PageHeader } from '../components/ui/PageHeader';
import { FolderKanban, Plus, Edit2, Trash2 } from 'lucide-react';

export interface Project {
  id: string;
  name: string;
  description: string;
  createdAt: string;
  updatedAt: string;
}

type SortOption = 'name-asc' | 'name-desc' | 'newest' | 'oldest';

const SORT_OPTIONS: { value: SortOption; label: string }[] = [
  { value: 'newest', label: 'Newest first' },
  { value: 'oldest', label: 'Oldest first' },
  { value: 'name-asc', label: 'Name (A–Z)' },
  { value: 'name-desc', label: 'Name (Z–A)' },
];

const Projects = () => {
  const { currentTenant } = useTenant();
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [search, setSearch] = useState('');
  const [sort, setSort] = useState<SortOption>('newest');

  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formName, setFormName] = useState('');
  const [formDesc, setFormDesc] = useState('');
  const [formError, setFormError] = useState('');
  const [formLoading, setFormLoading] = useState(false);

  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState('');
  const [deleteLoading, setDeleteLoading] = useState(false);

  const canManage = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';

  useEffect(() => {
    fetchProjects();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentTenant?.id]);

  const fetchProjects = async () => {
    if (!currentTenant) return;
    setLoading(true);
    setError('');
    try {
      const res = await client.get(`/api/tenants/${currentTenant.id}/projects`);
      setProjects(res.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load projects');
    } finally {
      setLoading(false);
    }
  };

  const filteredProjects = useMemo(() => {
    const q = search.toLowerCase();
    const filtered = projects.filter(
      (p) => p.name.toLowerCase().includes(q) || (p.description && p.description.toLowerCase().includes(q))
    );
    const sorted = [...filtered];
    switch (sort) {
      case 'name-asc':
        sorted.sort((a, b) => a.name.localeCompare(b.name));
        break;
      case 'name-desc':
        sorted.sort((a, b) => b.name.localeCompare(a.name));
        break;
      case 'oldest':
        sorted.sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime());
        break;
      case 'newest':
      default:
        sorted.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
    }
    return sorted;
  }, [projects, search, sort]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!currentTenant) return;
    setFormError('');
    setFormLoading(true);
    try {
      if (editingId) {
        await client.put(`/api/tenants/${currentTenant.id}/projects/${editingId}`, {
          name: formName,
          description: formDesc,
        });
      } else {
        await client.post(`/api/tenants/${currentTenant.id}/projects`, {
          name: formName,
          description: formDesc,
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
    setDeleteError('');
    setDeleteLoading(true);
    try {
      await client.delete(`/api/tenants/${currentTenant.id}/projects/${deleteId}`);
      setDeleteId(null);
      fetchProjects();
    } catch (err: any) {
      setDeleteError(err.response?.data?.message || 'Failed to delete project');
    } finally {
      setDeleteLoading(false);
    }
  };

  const openCreate = () => {
    setEditingId(null);
    setFormName('');
    setFormDesc('');
    setFormError('');
    setShowForm(true);
  };

  const openEdit = (project: Project) => {
    setEditingId(project.id);
    setFormName(project.name);
    setFormDesc(project.description || '');
    setFormError('');
    setShowForm(true);
  };

  if (!currentTenant) return null;

  return (
    <div>
      <PageHeader
        title="Projects"
        subtitle="Manage workspaces for your team's initiatives."
        actions={
          canManage ? (
            <Button onClick={openCreate}>
              <Plus size={16} /> Create project
            </Button>
          ) : undefined
        }
      />

      <ErrorState message={error} />

      <Modal isOpen={showForm} onClose={() => setShowForm(false)} title={editingId ? 'Edit project' : 'Create project'}>
        <form id="project-form" onSubmit={handleSubmit} noValidate>
          <ErrorState message={formError} />
          <Input
            label="Name"
            value={formName}
            onChange={(e) => setFormName(e.target.value)}
            required
            placeholder="e.g. Website Redesign"
            autoFocus
          />
          <div className="form-group">
            <label htmlFor="project-description">Description</label>
            <textarea
              id="project-description"
              value={formDesc}
              onChange={(e) => setFormDesc(e.target.value)}
              rows={3}
              placeholder="Brief description of the project"
            />
          </div>
        </form>
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setShowForm(false)}>
            Cancel
          </Button>
          <Button type="submit" form="project-form" isLoading={formLoading}>
            Save
          </Button>
        </div>
      </Modal>

      <ConfirmDialog
        isOpen={!!deleteId}
        title="Delete project"
        message="Are you sure you want to delete this project? This action cannot be undone and will delete all associated tasks."
        confirmText="Delete project"
        isDestructive
        isLoading={deleteLoading}
        onConfirm={handleDelete}
        onCancel={() => {
          setDeleteId(null);
          setDeleteError('');
        }}
      />
      {deleteError && <ErrorState message={deleteError} />}

      <div className="flex flex-col sm:flex-row gap-3 mb-4">
        <SearchInput value={search} onChange={setSearch} placeholder="Search projects..." className="max-w-md w-full" />
        <Select
          value={sort}
          onChange={(e) => setSort(e.target.value as SortOption)}
          options={SORT_OPTIONS}
          style={{ maxWidth: '200px' }}
          aria-label="Sort projects"
        />
      </div>

      {loading ? (
        <TableSkeleton rows={4} cols={4} />
      ) : filteredProjects.length === 0 ? (
        <EmptyState
          icon={<FolderKanban size={24} />}
          title="No projects found"
          description={search ? "We couldn't find any projects matching your search." : 'Get started by creating your first project.'}
          action={!search && canManage ? <Button onClick={openCreate}>Create project</Button> : undefined}
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
            {filteredProjects.map((p) => (
              <tr key={p.id}>
                <td>
                  <Link to={`/tenants/${currentTenant.id}/projects/${p.id}`} className="font-medium text-primary" style={{ textDecoration: 'none' }}>
                    {p.name}
                  </Link>
                </td>
                <td className="text-muted truncate" style={{ maxWidth: '300px' }}>
                  {p.description || '—'}
                </td>
                <td className="text-muted text-sm">{new Date(p.createdAt).toLocaleDateString()}</td>
                <td>
                  <div className="flex items-center gap-1">
                    {canManage && (
                      <>
                        <Button variant="ghost" size="sm" className="btn-icon" onClick={() => openEdit(p)} title="Edit">
                          <Edit2 size={16} />
                        </Button>
                        <Button variant="ghost" size="sm" className="btn-icon text-danger" onClick={() => setDeleteId(p.id)} title="Delete">
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
