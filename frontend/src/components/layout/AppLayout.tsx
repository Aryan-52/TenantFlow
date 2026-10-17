import { useEffect, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';
import { CommandPalette } from '../search/CommandPalette';
import { QuickCreateTask } from '../tasks/QuickCreateTask';
import { useGlobalShortcuts } from '../../hooks/useGlobalShortcuts';
import { useTenant } from '../../contexts/TenantContext';

const COLLAPSE_KEY = 'tf.sidebarCollapsed';

const readCollapsed = () => {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === '1';
  } catch {
    return false;
  }
};

export const AppLayout = ({ children }: { children: ReactNode }) => {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [creatingTask, setCreatingTask] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const { currentTenant } = useTenant();

  useGlobalShortcuts({
    onOpenPalette: () => setPaletteOpen(true),
    onCreate: currentTenant ? () => setCreatingTask(true) : undefined,
    onGoTo: (key) => {
      const t = currentTenant?.id;
      const routes: Record<string, string | null> = {
        d: t ? `/tenants/${t}/dashboard` : null,
        p: t ? `/tenants/${t}/projects` : null,
        t: t ? `/tenants/${t}/tasks` : null,
        m: t ? `/tenants/${t}/members` : null,
        w: t ? `/tenants/${t}/my-work` : null,
        c: t ? `/tenants/${t}/calendar` : null,
        a: t ? `/tenants/${t}/analytics` : null,
        i: '/notifications',
        n: '/notifications',
      };
      const path = routes[key];
      if (path) navigate(path);
    },
  });

  // Close the mobile drawer whenever the route changes (derived during render, not in an effect).
  const [lastPath, setLastPath] = useState(location.pathname);
  if (lastPath !== location.pathname) {
    setLastPath(location.pathname);
    setMobileNavOpen(false);
  }

  // Escape closes the mobile drawer.
  useEffect(() => {
    if (!mobileNavOpen) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setMobileNavOpen(false);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [mobileNavOpen]);

  const toggleCollapsed = () => {
    setCollapsed((c) => {
      try {
        localStorage.setItem(COLLAPSE_KEY, c ? '0' : '1');
      } catch {
        // Not persisted when storage is unavailable.
      }
      return !c;
    });
  };

  return (
    <div className={`app-shell ${collapsed ? 'is-collapsed' : ''}`.trim()}>
      <a href="#main-content" className="skip-link">Skip to content</a>
      {mobileNavOpen && <div className="sidebar-overlay" onClick={() => setMobileNavOpen(false)} />}
      <Sidebar
        mobileOpen={mobileNavOpen}
        onNavigate={() => setMobileNavOpen(false)}
        collapsed={collapsed}
        onToggleCollapsed={toggleCollapsed}
      />
      <div className="main-area">
        <Header
          onToggleMobileNav={() => setMobileNavOpen((o) => !o)}
          onOpenSearch={() => setPaletteOpen(true)}
          onCreateTask={currentTenant ? () => setCreatingTask(true) : undefined}
        />
        <main className="main-content" id="main-content" tabIndex={-1} style={{ outline: 'none' }}>
          <div className="page-container">{children}</div>
        </main>
      </div>
      {paletteOpen && (
        <CommandPalette onClose={() => setPaletteOpen(false)} onCreateTask={currentTenant ? () => setCreatingTask(true) : undefined} />
      )}
      {currentTenant && (
        <QuickCreateTask
          tenantId={currentTenant.id}
          isOpen={creatingTask}
          onClose={() => setCreatingTask(false)}
          // Open the new task where it lives, so the current page never shows stale lists.
          onCreated={(task) => navigate(`/tenants/${currentTenant.id}/projects/${task.projectId}/tasks?task=${task.id}`)}
          canDelete={currentTenant.myRole === 'ADMIN' || currentTenant.myRole === 'OWNER'}
        />
      )}
    </div>
  );
};
