import type { ReactNode } from 'react';
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
  LogOut,
  ChevronsUpDown,
  Check,
  Building2,
} from 'lucide-react';
import { Button } from '../ui/Button';
import { Dropdown, DropdownItem, DropdownLabel } from '../ui/Dropdown';

interface SidebarProps {
  mobileOpen: boolean;
  onNavigate: () => void;
}

export const Sidebar = ({ mobileOpen, onNavigate }: SidebarProps) => {
  const { currentTenant, tenants, setCurrentTenantId } = useTenant();
  const { logout } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();

  const handleTenantChange = (id: string) => {
    setCurrentTenantId(id);
    navigate(`/tenants/${id}/dashboard`);
  };

  const navItems: { name: string; icon: ReactNode; path: string | null }[] = [
    { name: 'Dashboard', icon: <LayoutDashboard size={18} />, path: currentTenant ? `/tenants/${currentTenant.id}/dashboard` : null },
    { name: 'Projects', icon: <FolderKanban size={18} />, path: currentTenant ? `/tenants/${currentTenant.id}/projects` : null },
    { name: 'Tasks', icon: <CheckSquare size={18} />, path: currentTenant ? `/tenants/${currentTenant.id}/tasks` : null },
    { name: 'Members', icon: <Users size={18} />, path: currentTenant ? `/tenants/${currentTenant.id}/members` : null },
  ];

  if (currentTenant && (currentTenant.myRole === 'ADMIN' || currentTenant.myRole === 'OWNER')) {
    navItems.push({ name: 'Settings', icon: <Settings size={18} />, path: `/tenants/${currentTenant.id}/settings` });
  }

  const isActive = (path: string | null) => {
    if (!path) return false;
    return location.pathname === path || location.pathname.startsWith(path + '/');
  };

  return (
    <aside className={`sidebar-container ${mobileOpen ? 'open' : ''}`.trim()}>
      <div className="sidebar-header">
        <Link to="/" className="sidebar-brand" style={{ textDecoration: 'none' }} onClick={onNavigate}>
          <span className="sidebar-brand-mark">T</span>
          TenantFlow
        </Link>
      </div>

      <div className="p-4 border-b">
        <p className="text-xs text-muted font-medium mb-2 uppercase" style={{ letterSpacing: '0.04em' }}>
          Workspace
        </p>
        {tenants.length > 0 ? (
          <Dropdown
            align="left"
            trigger={({ toggle }) => (
              <button
                type="button"
                onClick={toggle}
                className="flex items-center justify-between w-full gap-2 px-3 py-2 rounded-md border text-sm font-medium"
                style={{ backgroundColor: 'var(--bg-subtle)' }}
              >
                <span className="flex items-center gap-2 truncate">
                  <Building2 size={15} className="text-muted flex-shrink-0" />
                  <span className="truncate">{currentTenant?.name ?? 'Select workspace'}</span>
                </span>
                <ChevronsUpDown size={14} className="text-muted flex-shrink-0" />
              </button>
            )}
          >
            <DropdownLabel>Your workspaces</DropdownLabel>
            {tenants.map((t) => (
              <DropdownItem key={t.id} active={t.id === currentTenant?.id} onClick={() => handleTenantChange(t.id)}>
                <span className="flex-1 truncate">{t.name}</span>
                {t.id === currentTenant?.id && <Check size={14} />}
              </DropdownItem>
            ))}
          </Dropdown>
        ) : (
          <p className="text-muted text-sm">No workspace yet</p>
        )}
      </div>

      <nav className="sidebar-content">
        {navItems.map((item) =>
          item.path ? (
            <Link key={item.name} to={item.path} className={`sidebar-link ${isActive(item.path) ? 'active' : ''}`.trim()} onClick={onNavigate}>
              {item.icon}
              {item.name}
            </Link>
          ) : (
            <span key={item.name} className="sidebar-link" style={{ opacity: 0.45, cursor: 'not-allowed' }}>
              {item.icon}
              {item.name}
            </span>
          )
        )}
      </nav>

      <div className="sidebar-footer">
        <Link
          to="/profile"
          className={`sidebar-link mb-1 ${location.pathname === '/profile' ? 'active' : ''}`.trim()}
          onClick={onNavigate}
        >
          <User size={18} />
          Profile
        </Link>
        <Button variant="ghost" fullWidth onClick={logout} style={{ justifyContent: 'flex-start' }}>
          <LogOut size={18} />
          Sign Out
        </Button>
      </div>
    </aside>
  );
};
