import { useState } from 'react';
import { useTenant } from '../contexts/TenantContext';
import { useWorkspaceData } from '../hooks/useWorkspaceData';
import { PageHeader } from '../components/ui/PageHeader';
import { Select } from '../components/ui/Select';
import { ActivityFeed } from '../components/activity/ActivityFeed';

/** Workspace history: every recorded change, newest first, optionally for one project. */
const ActivityPage = () => {
  const { currentTenant } = useTenant();
  const tenantId = currentTenant?.id ?? '';
  const { projects } = useWorkspaceData(tenantId);
  const [projectId, setProjectId] = useState('');
  if (!currentTenant) return null;
  return (
    <div>
      <PageHeader title="Activity" subtitle="What changed in this workspace, as it was recorded" />
      <div className="toolbar">
        <Select
          aria-label="Project"
          value={projectId}
          onChange={(e) => setProjectId(e.target.value)}
          options={[{ value: '', label: 'All projects' }, ...projects.map((p) => ({ value: p.id, label: p.name }))]}
        />
      </div>
      <section className="panel" aria-label="Activity">
        {/* Keyed so switching project starts a fresh list. */}
        <ActivityFeed key={`${tenantId}:${projectId}`} tenantId={tenantId} projectId={projectId || undefined} pageSize={30} />
      </section>
    </div>
  );
};

export default ActivityPage;
