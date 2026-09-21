import { useAuth } from '../../contexts/AuthContext';
import { useTenant } from '../../contexts/TenantContext';

export const Header = () => {
  const { user } = useAuth();
  const { currentTenant } = useTenant();

  return (
    <header className="main-header justify-between">
      <div>
        <h2 style={{ fontSize: '1.125rem', margin: 0, fontWeight: 600 }}>
          {currentTenant ? currentTenant.name : 'Welcome to TenantFlow'}
        </h2>
      </div>
      <div className="flex items-center gap-3">
        <div style={{ textAlign: 'right' }}>
          <div className="text-sm font-semibold">{user?.name}</div>
          <div className="text-xs text-muted">{user?.email}</div>
        </div>
        <div style={{ width: 36, height: 36, borderRadius: '50%', backgroundColor: 'var(--primary-light)', color: 'var(--primary-color)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 'bold' }}>
          {user?.name.charAt(0).toUpperCase()}
        </div>
      </div>
    </header>
  );
};
