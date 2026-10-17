import { BrowserRouter, Routes, Route, Navigate, useParams, useNavigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { TenantProvider, useTenant } from './contexts/TenantContext';
import Login from './pages/Login';
import Register from './pages/Register';
import ForgotPassword from './pages/ForgotPassword';
import ResetPassword from './pages/ResetPassword';
import OAuthCallback from './pages/OAuthCallback';
import Dashboard from './pages/Dashboard';
import Projects from './pages/Projects';
import ProjectDetails from './pages/ProjectDetails';
import Tasks from './pages/Tasks';
import Members from './pages/Members';
import Settings from './pages/Settings';
import Profile from './pages/Profile';
import Notifications from './pages/Notifications';
import Analytics from './pages/Analytics';
import Welcome from './pages/Welcome';
import { hasSeenWelcome } from './pages/welcomeState';
import { ThemeProvider } from './contexts/ThemeContext';
import { ToastProvider } from './contexts/ToastContext';
import { SplashView, StartupSplash, useHoldSplash } from './components/layout/StartupSplash';
import { BrandMark } from './components/layout/BrandMark';
import { Button } from './components/ui/Button';
import { AppLayout } from './components/layout/AppLayout';
import { Suspense, lazy, useEffect } from 'react';

// Newer, larger areas load on first visit to keep the initial bundle small.
const MyWork = lazy(() => import('./pages/MyWork'));
const Portfolio = lazy(() => import('./pages/Portfolio'));
const Planning = lazy(() => import('./pages/Planning'));
const CalendarPage = lazy(() => import('./pages/CalendarPage'));
const Documents = lazy(() => import('./pages/Documents'));
const ActivityPage = lazy(() => import('./pages/ActivityPage'));
const Automations = lazy(() => import('./pages/Automations'));
const Reports = lazy(() => import('./pages/Reports'));
const Preferences = lazy(() => import('./pages/Preferences'));
const MemberDetail = lazy(() => import('./pages/MemberDetail'));

import type { ReactNode } from 'react';

/**
 * Shown while the session or a workspace loads. While the start-up splash is up it keeps it up
 * (useHoldSplash), and it looks identical, so the user sees one continuous screen; later (e.g.
 * right after signing in) it is the loading screen itself, without any minimum duration.
 */
const AppLoading = ({ label }: { label: string }) => {
  useHoldSplash();
  return <SplashView label={label} />;
};

/** The one start-up splash for a page load (see StartupSplash). */
const StartupSplashGate = () => {
  const { loading, user } = useAuth();
  return <StartupSplash authLoading={loading} signedIn={!!user} />;
};

/** The session could not be checked: say so truthfully (this is not a sign-out). */
const StartupError = ({ onRetry }: { onRetry: () => void }) => (
  <div className="startup-error" role="alert">
    <BrandMark size={40} />
    <h1>TenantFlow can't reach its server</h1>
    <p>Your session is kept. Check that the backend is running and your connection works, then try again.</p>
    <Button onClick={onRetry}>Try again</Button>
  </div>
);

const ProtectedRoute = ({ children }: { children: ReactNode }) => {
  const { user, loading, startupError, retryStartup } = useAuth();
  if (loading) return <AppLoading label="Restoring your session…" />;
  if (startupError) return <StartupError onRetry={retryStartup} />;
  // First visit without a session: a short introduction; returning visitors go straight to sign-in.
  if (!user) return <Navigate to={hasSeenWelcome() ? '/login' : '/welcome'} replace />;
  return <TenantProvider>{children}</TenantProvider>;
};

/**
 * Syncs the URL tenantId into TenantContext and guards invalid tenant IDs: an id the user
 * is not a member of redirects to their first workspace (the backend refuses it anyway).
 */
const TenantRoute = ({ children }: { children: ReactNode }) => {
  const { tenantId } = useParams();
  const { tenants, loading, setCurrentTenantId, currentTenant } = useTenant();
  const navigate = useNavigate();
  const currentTenantId = currentTenant?.id;

  // Sync URL tenantId into context after render (not during render)
  useEffect(() => {
    if (!tenantId || loading) return;
    if (tenants.length === 0) return;

    const exists = tenants.find(t => t.id === tenantId);
    if (exists) {
      // Only update if different to avoid unnecessary re-renders
      if (currentTenantId !== tenantId) {
        setCurrentTenantId(tenantId);
      }
    } else {
      // Invalid tenant ID — redirect to first available tenant
      navigate(`/tenants/${tenants[0].id}/dashboard`, { replace: true });
    }
  }, [tenantId, tenants, loading, currentTenantId, setCurrentTenantId, navigate]);

  if (loading) return <AppLoading label="Loading your workspace…" />;

  // If user has no tenants, show Dashboard in empty state (create workspace prompt)
  // instead of redirecting to /dashboard (which would cause a redirect loop)
  if (tenants.length === 0) {
    return <AppLayout><Dashboard /></AppLayout>;
  }

  // Until the context matches the URL, show nothing workspace-specific; the key remounts the
  // page on every workspace switch so no state or request of the previous one survives.
  if (!currentTenant || currentTenant.id !== tenantId) {
    return <AppLayout><PageLoading /></AppLayout>;
  }
  return (
    <AppLayout>
      <Suspense fallback={<PageLoading />}>
        <div key={tenantId} className="page-enter">{children}</div>
      </Suspense>
    </AppLayout>
  );
};

const PageLoading = () => <div className="page-loading" role="status" aria-label="Loading" />;

/**
 * Root router: redirects authenticated users to their active tenant's dashboard.
 */
const DashboardRouter = () => {
  const { tenants, loading, currentTenant } = useTenant();

  if (loading) return <AppLoading label="Loading…" />;

  if (tenants.length === 0) {
    // No tenants — show empty-state dashboard
    return <AppLayout><Dashboard /></AppLayout>;
  }

  const activeTenantId = currentTenant ? currentTenant.id : tenants[0].id;
  return <Navigate to={`/tenants/${activeTenantId}/dashboard`} replace />;
};

const App = () => {
  return (
    <ThemeProvider>
    <AuthProvider>
      <StartupSplashGate />
      <BrowserRouter>
        <ToastProvider>
        <Routes>
          <Route path="/welcome" element={<Welcome />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/forgot-password" element={<ForgotPassword />} />
          <Route path="/reset-password" element={<ResetPassword />} />
          <Route path="/oauth-callback" element={<OAuthCallback />} />

          {/* Root routes */}
          <Route path="/" element={<ProtectedRoute><DashboardRouter /></ProtectedRoute>} />
          <Route path="/dashboard" element={<ProtectedRoute><DashboardRouter /></ProtectedRoute>} />
          <Route path="/profile" element={<ProtectedRoute><AppLayout><Profile /></AppLayout></ProtectedRoute>} />
          <Route path="/notifications" element={<ProtectedRoute><AppLayout><Notifications /></AppLayout></ProtectedRoute>} />
          <Route path="/preferences" element={<ProtectedRoute><AppLayout><Suspense fallback={<PageLoading />}><Preferences /></Suspense></AppLayout></ProtectedRoute>} />

          {/* Tenant-scoped routes */}
          <Route path="/tenants/:tenantId/dashboard" element={<ProtectedRoute><TenantRoute><Dashboard /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/projects" element={<ProtectedRoute><TenantRoute><Projects /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/projects/:projectId/:view?" element={<ProtectedRoute><TenantRoute><ProjectDetails /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/tasks" element={<ProtectedRoute><TenantRoute><Tasks /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/analytics" element={<ProtectedRoute><TenantRoute><Analytics /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/members" element={<ProtectedRoute><TenantRoute><Members /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/settings" element={<ProtectedRoute><TenantRoute><Settings /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/my-work" element={<ProtectedRoute><TenantRoute><MyWork /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/portfolio" element={<ProtectedRoute><TenantRoute><Portfolio /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/planning" element={<ProtectedRoute><TenantRoute><Planning /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/calendar" element={<ProtectedRoute><TenantRoute><CalendarPage /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/documents" element={<ProtectedRoute><TenantRoute><Documents /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/activity" element={<ProtectedRoute><TenantRoute><ActivityPage /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/automations" element={<ProtectedRoute><TenantRoute><Automations /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/reports" element={<ProtectedRoute><TenantRoute><Reports /></TenantRoute></ProtectedRoute>} />
          <Route path="/tenants/:tenantId/members/:memberId" element={<ProtectedRoute><TenantRoute><MemberDetail /></TenantRoute></ProtectedRoute>} />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </ToastProvider>
      </BrowserRouter>
    </AuthProvider>
    </ThemeProvider>
  );
};

export default App;
