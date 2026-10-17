import { useTenant } from '../contexts/TenantContext';
import { PageHeader } from '../components/ui/PageHeader';
import { AnalyticsView } from '../components/analytics/AnalyticsView';

/** Workspace analytics: live, database-backed numbers for every project in the workspace. */
const Analytics = () => {
  const { currentTenant } = useTenant();
  if (!currentTenant) return null;
  return (
    <div>
      <PageHeader
        title="Analytics"
        subtitle={`How work is moving in ${currentTenant.name}: delivery, deadlines, workload and projects that need attention.`}
      />
      <AnalyticsView key={currentTenant.id} tenantId={currentTenant.id} />
    </div>
  );
};

export default Analytics;
