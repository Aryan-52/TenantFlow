import { useEffect, useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import client from '../api/client';
import { useTenant } from '../contexts/TenantContext';
import type { Project } from './Projects';
import type { Task } from './Tasks';
import { Button } from '../components/ui/Button';
import { ErrorState } from '../components/ui/ErrorState';
import { ConfirmDialog } from '../components/ui/ConfirmDialog';
import { Badge } from '../components/ui/Badge';
import { Skeleton } from '../components/ui/Skeleton';
import { ArrowLeft, Trash2, ExternalLink } from 'lucide-react';

const ProjectDetails = () => {
  const { projectId } = useParams();
  const { currentTenant } = useTenant();
  const navigate = useNavigate();
  
  const [project, setProject] = useState<Project | null>(null);
  const [tasks, setTasks] = useState<Task[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const canManage = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';

  useEffect(() => {
    if (currentTenant && projectId) {
      fetchProjectData();
    }
  }, [currentTenant, projectId]);

  const fetchProjectData = async () => {
    setLoading(true);
    try {
      const [projRes, tasksRes] = await Promise.all([
        client.get(`/api/tenants/${currentTenant!.id}/projects/${projectId}`),
        client.get(`/api/tenants/${currentTenant!.id}/projects/${projectId}/tasks`)
      ]);
      setProject(projRes.data);
      setTasks(tasksRes.data);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load project details');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!currentTenant || !project) return;
    setDeleteLoading(true);
    try {
      await client.delete(`/api/tenants/${currentTenant.id}/projects/${project.id}`);
      navigate(`/tenants/${currentTenant.id}/projects`);
    } catch (err: any) {
      alert('Failed to delete project: ' + (err.response?.data?.message || err.message));
      setDeleteLoading(false);
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

  const todoCount = tasks.filter(t => t.status === 'TODO').length;
  const inProgressCount = tasks.filter(t => t.status === 'IN_PROGRESS').length;
  const doneCount = tasks.filter(t => t.status === 'DONE').length;

  return (
    <div>
      <div className="mb-4">
        <Link to={`/tenants/${currentTenant?.id}/projects`} className="text-muted flex items-center gap-1 hover:text-main text-sm" style={{ textDecoration: 'none' }}>
          <ArrowLeft size={16} /> Back to Projects
        </Link>
      </div>

      <div className="flex justify-between items-start mb-6">
        <div>
          <h1 className="mb-2">{project.name}</h1>
          <p className="text-muted max-w-2xl">{project.description || 'No description provided.'}</p>
        </div>
        <div className="flex gap-2">
          {canManage && (
            <Button variant="danger" onClick={() => setDeleteId(project.id)}>
              <Trash2 size={16} /> Delete
            </Button>
          )}
        </div>
      </div>

      <ConfirmDialog
        isOpen={!!deleteId}
        title="Delete Project"
        message="Are you sure you want to delete this project? All associated tasks will be lost."
        confirmText="Delete Project"
        isDestructive
        isLoading={deleteLoading}
        onConfirm={handleDelete}
        onCancel={() => setDeleteId(null)}
      />

      <div className="grid grid-cols-4 gap-4 mb-6">
        <div className="card text-center p-4">
          <p className="text-muted text-sm font-medium mb-1">Total Tasks</p>
          <h2 className="mb-0">{tasks.length}</h2>
        </div>
        <div className="card text-center p-4" style={{ borderColor: 'var(--border-color)' }}>
          <p className="text-muted text-sm font-medium mb-1">To Do</p>
          <h2 className="mb-0">{todoCount}</h2>
        </div>
        <div className="card text-center p-4" style={{ borderColor: 'var(--primary-light)', backgroundColor: 'var(--primary-light)' }}>
          <p className="text-primary text-sm font-medium mb-1" style={{ color: 'var(--primary-color)' }}>In Progress</p>
          <h2 className="mb-0" style={{ color: 'var(--primary-hover)' }}>{inProgressCount}</h2>
        </div>
        <div className="card text-center p-4" style={{ borderColor: 'var(--success-bg)', backgroundColor: 'var(--success-bg)' }}>
          <p className="text-success text-sm font-medium mb-1" style={{ color: 'var(--success-text)' }}>Done</p>
          <h2 className="mb-0" style={{ color: 'var(--success-text)' }}>{doneCount}</h2>
        </div>
      </div>

      <div className="card">
        <div className="flex justify-between items-center mb-4">
          <h3 className="mb-0">Recent Tasks</h3>
          <Button variant="secondary" size="sm" onClick={() => navigate(`/tenants/${currentTenant?.id}/tasks?projectId=${project.id}`)}>
            View All Tasks <ExternalLink size={14} />
          </Button>
        </div>
        
        {tasks.length === 0 ? (
          <p className="text-muted text-center p-4">No tasks in this project yet.</p>
        ) : (
          <div className="flex flex-col gap-2">
            {tasks.slice(0, 5).map(t => (
              <div key={t.id} className="flex justify-between items-center p-3 border rounded-md" style={{ borderColor: 'var(--border-color)' }}>
                <div>
                  <div className="font-medium">{t.title}</div>
                  <div className="text-xs text-muted mt-1">
                    {t.status === 'TODO' && <Badge>TODO</Badge>}
                    {t.status === 'IN_PROGRESS' && <Badge color="blue">IN PROGRESS</Badge>}
                    {t.status === 'DONE' && <Badge color="green">DONE</Badge>}
                  </div>
                </div>
                <Badge color={t.priority === 'HIGH' ? 'red' : t.priority === 'MEDIUM' ? 'yellow' : 'blue'}>
                  {t.priority}
                </Badge>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default ProjectDetails;
