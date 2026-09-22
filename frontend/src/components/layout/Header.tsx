import { Menu, User, LogOut, ChevronDown } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../contexts/AuthContext';
import { useTenant } from '../../contexts/TenantContext';
import { Avatar } from '../ui/Avatar';
import { Dropdown, DropdownItem, DropdownDivider } from '../ui/Dropdown';

interface HeaderProps {
  onToggleMobileNav: () => void;
}

export const Header = ({ onToggleMobileNav }: HeaderProps) => {
  const { user, logout } = useAuth();
  const { currentTenant } = useTenant();
  const navigate = useNavigate();

  if (!user) return null;

  return (
    <header className="main-header">
      <div className="flex items-center gap-3" style={{ minWidth: 0 }}>
        <button type="button" className="menu-toggle" onClick={onToggleMobileNav} aria-label="Open navigation">
          <Menu size={20} />
        </button>
        <h2 className="truncate" style={{ fontSize: 'var(--text-lg)', fontWeight: 600 }}>
          {currentTenant ? currentTenant.name : 'Welcome to TenantFlow'}
        </h2>
      </div>

      <Dropdown
        align="right"
        trigger={({ toggle }) => (
          <button type="button" onClick={toggle} className="flex items-center gap-2" style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
            <div className="text-right hidden md:block">
              <div className="text-sm font-semibold">{user.name}</div>
              <div className="text-xs text-muted">{user.email}</div>
            </div>
            <Avatar name={user.name} />
            <ChevronDown size={14} className="text-muted" />
          </button>
        )}
      >
        <div className="px-3 py-2 md:hidden">
          <div className="text-sm font-semibold">{user.name}</div>
          <div className="text-xs text-muted truncate">{user.email}</div>
        </div>
        <DropdownItem icon={<User size={15} />} onClick={() => navigate('/profile')}>
          Your profile
        </DropdownItem>
        <DropdownDivider />
        <DropdownItem icon={<LogOut size={15} />} danger onClick={logout}>
          Sign out
        </DropdownItem>
      </Dropdown>
    </header>
  );
};
