import React from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTenant } from '../../contexts/TenantContext';
import { useAuth } from '../../contexts/AuthContext';
import {
  LayoutDashboard,
  FolderKanban,
  CheckSquare,
  Users,
  Settings,
  User,
  LogOut
} from 'lucide-react';
import { Button } from '../ui/Button';

export const Sidebar = () => {
  const { currentTenant, tenants, setCurrentTenantId } = useTenant();
  const { logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const handleTenantChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const id = e.target.value;
    if (id) {
      setCurrentTenantId(id);
      navigate(`/tenants/${id}/dashboard`);
    }
  };

  // Build nav items; path is null when no workspace is selected (prevents invalid navigation)
  const navItems: { name: string; icon: React.ReactNode; path: string | null }[] = [
    { name: 'Dashboard', icon: <LayoutDashboard size={18} />, path: currentTenant ? `/tenants/${currentTenant.id}/dashboard` : null },
    { name: 'Projects',  icon: <FolderKanban size={18} />,   path: currentTenant ? `/tenants/${currentTenant.id}/projects`  : null },
    { name: 'Tasks',     icon: <CheckSquare size={18} />,    path: currentTenant ? `/tenants/${currentTenant.id}/tasks`     : null },
    { name: 'Members',   icon: <Users size={18} />,          path: currentTenant ? `/tenants/${currentTenant.id}/members`   : null },
  ];

  if (currentTenant && (currentTenant.myRole === 'ADMIN' || currentTenant.myRole === 'OWNER')) {
    navItems.push({ name: 'Settings', icon: <Settings size={18} />, path: `/tenants/${currentTenant.id}/settings` });
  }

  const isActive = (path: string | null) => {
    if (!path) return false;
    return location.pathname === path || location.pathname.startsWith(path + '/');
  };

  return (
    <aside className="sidebar-container">
      <div className="sidebar-header">
        <Link to="/" style={{ color: 'var(--primary-color)', fontWeight: 700, fontSize: '1.25rem', textDecoration: 'none', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
          <div style={{ width: 24, height: 24, backgroundColor: 'var(--primary-color)', borderRadius: 4, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontSize: '0.875rem' }}>T</div>
          TenantFlow
        </Link>
      </div>

      <div className="p-4" style={{ borderBottom: '1px solid var(--border-color)' }}>
        <p className="text-xs text-muted mb-1 font-medium" style={{ textTransform: 'uppercase' }}>Workspace</p>
        {tenants.length > 0 ? (
          <select
            value={currentTenant?.id || ''}
            onChange={handleTenantChange}
            style={{ padding: '0.375rem', width: '100%', borderRadius: '4px', border: '1px solid var(--border-color)', background: 'var(--bg-subtle)' }}
          >
            {tenants.map(t => (
              <option key={t.id} value={t.id}>{t.name}</option>
            ))}
          </select>
        ) : (
          <p className="text-muted text-sm">No workspace</p>
        )}
      </div>

      <nav className="sidebar-content">
        <ul style={{ listStyle: 'none', margin: 0, padding: 0 }}>
          {navItems.map((item) => {
            const active = isActive(item.path);
            const linkStyle: React.CSSProperties = {
              display: 'flex',
              alignItems: 'center',
              gap: '0.75rem',
              padding: '0.5rem 0.75rem',
              borderRadius: 'var(--radius-md)',
              textDecoration: 'none',
              color: active ? 'var(--primary-color)' : 'var(--text-secondary)',
              backgroundColor: active ? 'var(--primary-light)' : 'transparent',
              fontWeight: active ? 600 : 500,
              opacity: item.path ? 1 : 0.45,
              cursor: item.path ? 'pointer' : 'not-allowed',
            };
            return (
              <li key={item.name} style={{ marginBottom: '0.25rem' }}>
                {item.path ? (
                  <Link to={item.path} style={linkStyle}>
                    {item.icon}
                    {item.name}
                  </Link>
                ) : (
                  <span style={linkStyle}>
                    {item.icon}
                    {item.name}
                  </span>
                )}
              </li>
            );
          })}
        </ul>
      </nav>

      <div className="sidebar-footer">
        <Link
          to="/profile"
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '0.75rem',
            padding: '0.5rem 0.75rem',
            borderRadius: 'var(--radius-md)',
            textDecoration: 'none',
            color: location.pathname === '/profile' ? 'var(--primary-color)' : 'var(--text-secondary)',
            backgroundColor: location.pathname === '/profile' ? 'var(--primary-light)' : 'transparent',
            fontWeight: 500,
            marginBottom: '0.5rem',
          }}
        >
          <User size={18} />
          Profile
        </Link>
        <Button variant="ghost" onClick={logout} style={{ width: '100%', justifyContent: 'flex-start', color: 'var(--text-secondary)' }}>
          <LogOut size={18} />
          Sign Out
        </Button>
      </div>
    </aside>
  );
};
