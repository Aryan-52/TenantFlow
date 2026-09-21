import { BrowserRouter, Routes, Route, Navigate, useParams, useNavigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { TenantProvider, useTenant } from './contexts/TenantContext';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import Projects from './pages/Projects';
import ProjectDetails from './pages/ProjectDetails';
import Tasks from './pages/Tasks';
import Members from './pages/Members';
import Settings from './pages/Settings';
import Profile from './pages/Profile';
import { AppLayout } from './components/layout/AppLayout';
import { useEffect } from 'react';

import type { ReactNode } from 'react';

const ProtectedRoute = ({ children }: { children: ReactNode }) => {
  const { user, loading } = useAuth();
  if (loading) return <div className="app-loading">Loading...</div>;
  if (!user) return <Navigate to="/login" replace />;
  return <TenantProvider>{children}</TenantProvider>;
};

/**
 * Syncs the URL tenantId into TenantContext and guards invalid tenant IDs.
 * Does NOT use setTimeout — uses useEffect which is safe after render.
 */
const TenantRoute = ({ children }: { children: ReactNode }) => {
  const { tenantId } = useParams();
  const { tenants, loading, setCurrentTenantId, currentTenant } = useTenant();
  const navigate = useNavigate();

  // Sync URL tenantId into context after render (not during render)
  useEffect(() => {
    if (!tenantId || loading) return;
    if (tenants.length === 0) return;

    const exists = tenants.find(t => t.id === tenantId);
    if (exists) {
      // Only update if different to avoid unnecessary re-renders
      if (!currentTenant || currentTenant.id !== tenantId) {
        setCurrentTenantId(tenantId);
      }
    } else {
      // Invalid tenant ID — redirect to first available tenant
      navigate(`/tenants/${tenants[0].id}/dashboard`, { replace: true });
    }
  }, [tenantId, tenants, loading]);

  if (loading) return <div className="app-loading">Loading workspace...</div>;

  // If user has no tenants, show Dashboard in empty state (create workspace prompt)
  // instead of redirecting to /dashboard (which would cause a redirect loop)
  if (tenants.length === 0) {
    return <AppLayout><Dashboard /></AppLayout>;
  }

  return <AppLayout>{children}</AppLayout>;
};

/**
 * Root router: redirects authenticated users to their active tenant's dashboard.
 */
const DashboardRouter = () => {
  const { tenants, loading, currentTenant } = useTenant();

  if (loading) return <div className="app-loading">Loading...</div>;

  if (tenants.length === 0) {
    // No tenants — show empty-state dashboard
    return <AppLayout><Dashboard /></AppLayout>;
  }

  const activeTenantId = currentTenant ? currentTenant.id : tenants[0].id;
  return <Navigate to={`/tenants/${activeTenantId}/dashboard`} replace />;
};

const App = () => {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />

          {/* Root routes */}
          <Route path="/" element={<ProtectedRoute><DashboardRouter /></ProtectedRoute>} />
          <Route path="/dashboard" element={<ProtectedRoute><DashboardRouter /></ProtectedRoute>} />
          <Route path="/profile" element={<ProtectedRoute><AppLayout><Profile /></AppLayout></ProtectedRoute>} />

          {/* Tenant-scoped routes */}
          <Route path="/tenants/:tenantId/dashboard" element={<ProtectedRoute><TenantRoute><Dashboard /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/projects" element={<ProtectedRoute><TenantRoute><Projects /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/projects/:projectId" element={<ProtectedRoute><TenantRoute><ProjectDetails /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/tasks" element={<ProtectedRoute><TenantRoute><Tasks /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/members" element={<ProtectedRoute><TenantRoute><Members /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/settings" element={<ProtectedRoute><TenantRoute><Settings /></TenantRoute></ProtectedRoute>} />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
};

export default App;
