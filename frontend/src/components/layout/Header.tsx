import { Menu, User, LogOut, ChevronDown, Search, Sun, Moon, Monitor, Plus, SlidersHorizontal } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { useTenant } from '../../contexts/TenantContext';
import { useTheme, type ThemePreference } from '../../contexts/ThemeContext';
import { Avatar } from '../ui/Avatar';
import { Dropdown, DropdownItem, DropdownDivider, DropdownLabel } from '../ui/Dropdown';
import { Breadcrumbs, type Crumb } from '../ui/PageHeader';
import { NotificationBell } from '../notifications/NotificationBell';

interface HeaderProps {
  onToggleMobileNav: () => void;
  onOpenSearch: () => void;
  /** Opens the global "New task" dialog; hidden outside a workspace. */
  onCreateTask?: () => void;
}

const SECTIONS: Record<string, string> = {
  dashboard: 'Overview',
  'my-work': 'My Work',
  projects: 'Projects',
  tasks: 'All tasks',
  portfolio: 'Portfolio',
  planning: 'Planning',
  calendar: 'Calendar',
  analytics: 'Analytics',
  members: 'Team',
  documents: 'Documents',
  activity: 'Activity',
  automations: 'Automations',
  reports: 'Reports',
  settings: 'Workspace settings',
};

/** Where am I: workspace > section, derived from the route. */
function useLocationCrumbs(): Crumb[] {
  const { pathname } = useLocation();
  const { currentTenant } = useTenant();
  const tenantMatch = pathname.match(/^\/tenants\/([^/]+)\/([^/]+)/);
  if (tenantMatch && currentTenant) {
    const section = SECTIONS[tenantMatch[2]] ?? '';
    return [
      { label: currentTenant.name, to: `/tenants/${currentTenant.id}/dashboard` },
      ...(section ? [{ label: section, to: `/tenants/${currentTenant.id}/${tenantMatch[2]}` }] : []),
    ];
  }
  if (pathname.startsWith('/profile')) return [{ label: 'Profile' }];
  if (pathname.startsWith('/notifications')) return [{ label: 'Inbox' }];
  if (pathname.startsWith('/preferences')) return [{ label: 'Preferences' }];
  return currentTenant ? [{ label: currentTenant.name }] : [{ label: 'TenantFlow' }];
}

const THEME_OPTIONS: { value: ThemePreference; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'System', icon: Monitor },
];

export const Header = ({ onToggleMobileNav, onOpenSearch, onCreateTask }: HeaderProps) => {
  const { user, logout } = useAuth();
  const { resolved, preference, setPreference } = useTheme();
  const navigate = useNavigate();
  const crumbs = useLocationCrumbs();

  if (!user) return null;

  const isMac = typeof navigator !== 'undefined' && navigator.platform.toLowerCase().includes('mac');

  return (
    <header className="main-header">
      <div className="header-left">
        <button type="button" className="icon-button menu-toggle" onClick={onToggleMobileNav} aria-label="Open navigation">
          <Menu size={19} />
        </button>
        <Breadcrumbs items={crumbs} className="header-crumbs" />
      </div>

      <div className="header-right">
        <button type="button" className="header-search" onClick={onOpenSearch} aria-label="Search and commands" aria-keyshortcuts="Control+K Meta+K /">
          <Search size={15} />
          <span className="header-search-label">Search or jump to…</span>
          <kbd className="header-search-kbd">{isMac ? '⌘K' : 'Ctrl K'}</kbd>
        </button>
        {onCreateTask && (
          <button type="button" className="btn btn-primary btn-sm header-create" onClick={onCreateTask} aria-label="New task" aria-keyshortcuts="C">
            <Plus size={15} aria-hidden="true" /> <span className="header-create-label">New task</span>
          </button>
        )}
        <button
          type="button"
          className="icon-button"
          onClick={() => setPreference(resolved === 'dark' ? 'light' : 'dark')}
          aria-label={resolved === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
          data-tooltip={resolved === 'dark' ? 'Light theme' : 'Dark theme'}
        >
          {resolved === 'dark' ? <Sun size={17} /> : <Moon size={17} />}
        </button>
        <NotificationBell />
        <span className="header-divider" aria-hidden="true" />
        <Dropdown
          align="right"
          trigger={({ open, toggle }) => (
            <button type="button" onClick={toggle} className="header-user" aria-haspopup="menu" aria-expanded={open} aria-label={`Account: ${user.name}`}>
              <Avatar name={user.name} size="sm" />
              <span className="header-user-name">{user.name}</span>
              <ChevronDown size={14} className="text-muted" />
            </button>
          )}
        >
          <div className="user-menu-head">
            <Avatar name={user.name} size="md" />
            <div className="text">
              <div className="text-sm font-semibold truncate">{user.name}</div>
              <div className="text-xs text-muted truncate">{user.email}</div>
            </div>
          </div>
          <DropdownDivider />
          <DropdownItem icon={<User size={15} />} onClick={() => navigate('/profile')}>
            Your profile
          </DropdownItem>
          <DropdownItem icon={<SlidersHorizontal size={15} />} onClick={() => navigate('/preferences')}>
            Preferences
          </DropdownItem>
          <DropdownLabel>Appearance</DropdownLabel>
          <div className="theme-options" role="group" aria-label="Theme" onClick={(e) => e.stopPropagation()}>
            {THEME_OPTIONS.map(({ value, label, icon: Icon }) => (
              <button key={value} type="button" aria-pressed={preference === value} onClick={() => setPreference(value)}>
                <Icon size={13} aria-hidden="true" /> {label}
              </button>
            ))}
          </div>
          <DropdownDivider />
          <DropdownItem icon={<LogOut size={15} />} danger onClick={logout}>
            Sign out
          </DropdownItem>
        </Dropdown>
      </div>
    </header>
  );
};
