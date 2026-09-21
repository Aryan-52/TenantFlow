import type { ReactNode } from 'react';
import { Sidebar } from './Sidebar';
import { Header } from './Header';

export const AppLayout = ({ children }: { children: ReactNode }) => {
  return (
    <div className="app-shell">
      <Sidebar />
      <div className="main-area">
        <Header />
        <main className="main-content">
          {children}
        </main>
      </div>
    </div>
  );
};
