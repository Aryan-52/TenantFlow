import { useEffect, useState, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';

export const AppLayout = ({ children }: { children: ReactNode }) => {
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const location = useLocation();

  // Close the mobile nav drawer automatically whenever the route changes.
  useEffect(() => {
    setMobileNavOpen(false);
  }, [location.pathname]);

  return (
    <div className="app-shell">
      {mobileNavOpen && <div className="sidebar-overlay" onClick={() => setMobileNavOpen(false)} />}
      <Sidebar mobileOpen={mobileNavOpen} onNavigate={() => setMobileNavOpen(false)} />
      <div className="main-area">
        <Header onToggleMobileNav={() => setMobileNavOpen((o) => !o)} />
        <main className="main-content">{children}</main>
      </div>
    </div>
  );
};
