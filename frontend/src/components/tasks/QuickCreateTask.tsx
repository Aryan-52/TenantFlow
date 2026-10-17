import { useEffect, useState } from 'react';
import client from '../../api/client';
import { apiError, projectApi, type Project, type Task } from '../../api/work';
import type { Member } from '../../pages/Members';
import { useToast } from '../../contexts/ToastContext';
import { TaskModal } from './TaskModal';

interface QuickCreateTaskProps {
  tenantId: string;
  isOpen: boolean;
  onClose: () => void;
  onCreated?: (task: Task) => void;
  canDelete?: boolean;
}

/**
 * "New task" from anywhere in a workspace: loads the projects and members only when opened,
 * then shows the regular task form with a project picker.
 */
export const QuickCreateTask = ({ tenantId, isOpen, onClose, onCreated, canDelete = false }: QuickCreateTaskProps) => {
  const toast = useToast();
  const [data, setData] = useState<{ projects: Project[]; members: Member[] } | null>(null);

  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;
    Promise.all([projectApi.list(tenantId), client.get<Member[]>(`/api/tenants/${tenantId}/members`)])
      .then(([projects, members]) => {
        if (cancelled) return;
        const open = projects.filter((p) => p.status !== 'ARCHIVED');
        if (open.length === 0) {
          toast.error('Create a project first: every task belongs to a project.');
          onClose();
          return;
        }
        setData({ projects: open, members: members.data });
      })
      .catch((err) => {
        if (cancelled) return;
        toast.error(apiError(err, 'Could not load projects').message);
        onClose();
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, tenantId]);

  if (!isOpen || !data) return null;

  return (
    <TaskModal
      isOpen
      onClose={() => {
        setData(null);
        onClose();
      }}
      tenantId={tenantId}
      projectId=""
      task={null}
      members={data.members}
      projects={data.projects}
      canDelete={canDelete}
      chooseProject
      onSaved={(t) => onCreated?.(t)}
      onDeleted={() => undefined}
    />
  );
};
