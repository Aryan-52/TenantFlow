import { useState, type ReactNode } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useTenant } from '../../contexts/TenantContext';
import {
  Activity,
  BarChart3,
  Bell,
  CalendarDays,
  Check,
  ChevronDown,
  ChevronsUpDown,
  FileStack,
  FileText,
  FolderKanban,
  SquareChartGantt,
  Inbox,
  LayoutDashboard,
  ListChecks,
  PanelLeftClose,
  PanelLeftOpen,
  Plus,
  Settings,
  SlidersHorizontal,
  Sun,
  User,
  Users,
  Workflow,
  Briefcase,
} from 'lucide-react';
import { Dropdown, DropdownDivider, DropdownItem, DropdownLabel } from '../ui/Dropdown';
import { CreateWorkspaceModal } from './CreateWorkspaceModal';
import { SearchInput } from '../ui/SearchInput';
import { BrandMark } from './BrandMark';
import { initials, tintStyle } from '../ui/visual';

interface SidebarProps {
  mobileOpen: boolean;
  onNavigate: () => void;
  collapsed: boolean;
  onToggleCollapsed: () => void;
}

interface NavItem {
  name: string;
  icon: ReactNode;
  path: string;
  /** Extra path prefixes that also mark this item active. */
  also?: string[];
}

interface NavGroup {
  id: string;
  label: string;
  items: NavItem[];
}

const ROLE_LABEL: Record<string, string> = { OWNER: 'Owner', ADMIN: 'Admin', MEMBER: 'Member' };
const GROUPS_KEY = 'tf.navGroupsClosed';
const SEARCH_THRESHOLD = 6;

const readClosed = (): string[] => {
  try {
    const v = JSON.parse(localStorage.getItem(GROUPS_KEY) ?? '[]');
    return Array.isArray(v) ? v.filter((x) => typeof x === 'string') : [];
  } catch {
    return [];
  }
};

/** The navigation rail: workspace switcher, grouped destinations and collapse control. */
export const Sidebar = ({ mobileOpen, onNavigate, collapsed, onToggleCollapsed }: SidebarProps) => {
  const { currentTenant, tenants, setCurrentTenantId } = useTenant();
  const location = useLocation();
  const navigate = useNavigate();
  const [creating, setCreating] = useState(false);
  const [closed, setClosed] = useState<string[]>(readClosed);
  const [workspaceQuery, setWorkspaceQuery] = useState('');

  const switchTo = (id: string) => {
    setWorkspaceQuery('');
    if (id === currentTenant?.id) return;
    setCurrentTenantId(id);
    // Workspace-scoped pages remount under the new id, so nothing from the old one stays.
    navigate(`/tenants/${id}/dashboard`);
    onNavigate();
  };

  const toggleGroup = (id: string) => {
    setClosed((prev) => {
      const next = prev.includes(id) ? prev.filter((g) => g !== id) : [...prev, id];
      try {
        localStorage.setItem(GROUPS_KEY, JSON.stringify(next));
      } catch {
        // Not persisted when storage is unavailable.
      }
      return next;
    });
  };

  const t = currentTenant?.id;
  const isManager = currentTenant?.myRole === 'ADMIN' || currentTenant?.myRole === 'OWNER';

  // Only real, working destinations; nothing is shown as a placeholder.
  const groups: NavGroup[] = [
    ...(t
      ? [
          {
            id: 'workspace',
            label: 'Workspace',
            items: [
              { name: 'Overview', icon: <LayoutDashboard size={16} />, path: `/tenants/${t}/dashboard` },
              { name: 'My Work', icon: <Sun size={16} />, path: `/tenants/${t}/my-work` },
              { name: 'Inbox', icon: <Inbox size={16} />, path: '/notifications' },
              { name: 'Projects', icon: <FolderKanban size={16} />, path: `/tenants/${t}/projects` },
              { name: 'All tasks', icon: <ListChecks size={16} />, path: `/tenants/${t}/tasks` },
              { name: 'Portfolio', icon: <Briefcase size={16} />, path: `/tenants/${t}/portfolio` },
              { name: 'Planning', icon: <SquareChartGantt size={16} />, path: `/tenants/${t}/planning` },
              { name: 'Calendar', icon: <CalendarDays size={16} />, path: `/tenants/${t}/calendar` },
              { name: 'Analytics', icon: <BarChart3 size={16} />, path: `/tenants/${t}/analytics` },
            ],
          },
          {
            id: 'collaboration',
            label: 'Collaboration',
            items: [
              { name: 'Team', icon: <Users size={16} />, path: `/tenants/${t}/members` },
              { name: 'Documents', icon: <FileStack size={16} />, path: `/tenants/${t}/documents` },
              { name: 'Activity', icon: <Activity size={16} />, path: `/tenants/${t}/activity` },
            ],
          },
          {
            id: 'manage',
            label: 'Manage',
            items: [
              { name: 'Automations', icon: <Workflow size={16} />, path: `/tenants/${t}/automations` },
              { name: 'Reports', icon: <FileText size={16} />, path: `/tenants/${t}/reports` },
              ...(isManager ? [{ name: 'Workspace settings', icon: <Settings size={16} />, path: `/tenants/${t}/settings` }] : []),
            ],
          },
        ]
      : []),
    {
      id: 'personal',
      label: 'Personal',
      items: [
        ...(t ? [] : [{ name: 'Inbox', icon: <Bell size={16} />, path: '/notifications' }]),
        { name: 'Profile', icon: <User size={16} />, path: '/profile' },
        { name: 'Preferences', icon: <SlidersHorizontal size={16} />, path: '/preferences' },
      ],
    },
  ];

  const isActive = (item: NavItem) =>
    [item.path, ...(item.also ?? [])].some((p) => location.pathname === p || location.pathname.startsWith(p + '/'));

  const renderLink = (item: NavItem) => {
    const active = isActive(item);
    return (
      <Link
        key={item.path}
        to={item.path}
        className={`sidebar-link ${active ? 'active' : ''}`.trim()}
        aria-current={active ? 'page' : undefined}
        onClick={onNavigate}
        data-tooltip={item.name}
        data-tooltip-side="right"
        aria-label={collapsed ? item.name : undefined}
      >
        {item.icon}
        <span className="sidebar-link-label">{item.name}</span>
      </Link>
    );
  };

  const q = workspaceQuery.trim().toLowerCase();
  const visibleWorkspaces = q ? tenants.filter((w) => w.name.toLowerCase().includes(q)) : tenants;

  return (
    <aside className={`sidebar-container ${mobileOpen ? 'open' : ''}`.trim()} aria-label="Main navigation">
      <div className="sidebar-header">
        <Link to="/" className="sidebar-brand" onClick={onNavigate} aria-label="TenantFlow home">
          <BrandMark className="sidebar-brand-mark" />
          <span className="sidebar-brand-name">TenantFlow</span>
        </Link>
      </div>

      <div className="workspace-switcher">
        {tenants.length > 0 ? (
          <Dropdown
            align="left"
            trigger={({ open, toggle }) => (
              <button
                type="button"
                onClick={toggle}
                className="workspace-trigger"
                aria-haspopup="menu"
                aria-expanded={open}
                aria-label={`Workspace: ${currentTenant?.name ?? 'none selected'}${currentTenant ? `, your role ${ROLE_LABEL[currentTenant.myRole] ?? currentTenant.myRole}` : ''}. Switch workspace`}
                data-tooltip={currentTenant?.name ?? 'Workspace'}
                data-tooltip-side="right"
              >
                <span className="workspace-mark" style={tintStyle(currentTenant?.id ?? 'none')} aria-hidden="true">
                  {initials(currentTenant?.name ?? '?')}
                </span>
                <span className="workspace-text">
                  <span className="workspace-name">{currentTenant?.name ?? 'Select workspace'}</span>
                  {currentTenant && <span className="workspace-role">{ROLE_LABEL[currentTenant.myRole] ?? currentTenant.myRole}</span>}
                </span>
                <ChevronsUpDown size={14} className="flex-shrink-0 workspace-chevron" aria-hidden="true" />
              </button>
            )}
          >
            <div className="workspace-menu">
              <DropdownLabel>Workspaces</DropdownLabel>
              {tenants.length >= SEARCH_THRESHOLD && (
                <div className="workspace-search" onClick={(e) => e.stopPropagation()}>
                  <SearchInput value={workspaceQuery} onChange={setWorkspaceQuery} placeholder="Find a workspace" />
                </div>
              )}
              <div className="workspace-list">
                {visibleWorkspaces.map((w) => (
                  <DropdownItem key={w.id} active={w.id === currentTenant?.id} onClick={() => switchTo(w.id)}>
                    <span className="workspace-mark workspace-mark-sm" style={tintStyle(w.id)} aria-hidden="true">{initials(w.name)}</span>
                    <span className="flex-1 truncate">{w.name}</span>
                    <span className="workspace-option-role">{ROLE_LABEL[w.myRole] ?? w.myRole}</span>
                    {w.id === currentTenant?.id && <Check size={14} aria-label="Current workspace" />}
                  </DropdownItem>
                ))}
                {visibleWorkspaces.length === 0 && <p className="text-xs text-muted px-3 py-2">No workspace matches.</p>}
              </div>
              <DropdownDivider />
              <DropdownItem icon={<Plus size={15} />} onClick={() => setCreating(true)}>
                New workspace
              </DropdownItem>
              {t && isManager && (
                <DropdownItem icon={<Settings size={15} />} onClick={() => navigate(`/tenants/${t}/settings`)}>
                  Workspace settings
                </DropdownItem>
              )}
            </div>
          </Dropdown>
        ) : (
          <button type="button" className="workspace-trigger" onClick={() => setCreating(true)}>
            <Plus size={15} aria-hidden="true" />
            <span className="workspace-text"><span className="workspace-name">Create a workspace</span></span>
          </button>
        )}
      </div>

      <nav className="sidebar-content" aria-label="Sections">
        {groups.map((g) => {
          const open = !closed.includes(g.id);
          const containsActive = g.items.some(isActive);
          return (
            <div className={`nav-group ${g.id === 'workspace' ? '' : 'is-secondary'}`.trim()} key={g.id}>
              <button
                type="button"
                className="nav-group-toggle"
                aria-expanded={open || containsActive}
                aria-controls={`nav-${g.id}`}
                onClick={() => toggleGroup(g.id)}
              >
                <ChevronDown size={12} aria-hidden="true" />
                {g.label}
              </button>
              {/* A group holding the current page never hides it. */}
              <div id={`nav-${g.id}`} className="nav-group-items" hidden={!open && !containsActive}>
                {g.items.map(renderLink)}
              </div>
            </div>
          );
        })}
      </nav>

      <div className="sidebar-footer sidebar-collapse-row">
        <button
          type="button"
          className="btn btn-ghost btn-sm sidebar-collapse"
          onClick={onToggleCollapsed}
          aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
          aria-pressed={collapsed}
          data-tooltip={collapsed ? 'Expand sidebar' : undefined}
          data-tooltip-side="right"
        >
          {collapsed ? <PanelLeftOpen size={16} /> : <PanelLeftClose size={16} />}
          <span className="sidebar-collapse-label">Collapse</span>
        </button>
      </div>
      <CreateWorkspaceModal isOpen={creating} onClose={() => setCreating(false)} />
    </aside>
  );
};
