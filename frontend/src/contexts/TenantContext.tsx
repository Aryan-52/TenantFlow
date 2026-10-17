import { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { ReactNode } from 'react';
import client from '../api/client';
import { useAuth } from './AuthContext';


export interface Tenant {
  id: string;
  name: string;
  slug: string;
  myRole: string;
}

interface TenantContextType {
  tenants: Tenant[];
  currentTenant: Tenant | null;
  loading: boolean;
  setCurrentTenantId: (id: string) => void;
  refreshTenants: () => Promise<void>;
  /** Add a newly created tenant immediately without a full re-fetch */
  addTenant: (tenant: Tenant) => void;
}

const TenantContext = createContext<TenantContextType | undefined>(undefined);

export const TenantProvider = ({ children }: { children: ReactNode }) => {
  const { user } = useAuth();
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [currentTenantId, setCurrentTenantIdState] = useState<string | null>(
    localStorage.getItem('tenantId')
  );
  const [loading, setLoading] = useState(true);

  /** Stores the workspaces and makes sure the remembered current one still exists. */
  const applyTenants = useCallback((data: Tenant[]) => {
    setTenants(data);
    if (data.length > 0) {
      const storedId = localStorage.getItem('tenantId');
      if (!storedId || !data.find(t => t.id === storedId)) {
        setCurrentTenantIdState(data[0].id);
        localStorage.setItem('tenantId', data[0].id);
      }
    } else {
      setCurrentTenantIdState(null);
      localStorage.removeItem('tenantId');
    }
  }, []);

  /** Reload on demand (e.g. after creating or leaving a workspace). */
  const fetchTenants = useCallback(async () => {
    if (!user) return;
    try {
      const res = await client.get<Tenant[]>('/api/tenants');
      applyTenants(res.data);
    } catch (err) {
      console.error('Failed to fetch tenants', err);
    }
  }, [user, applyTenants]);

  // Initial load for the signed-in user. A late response after sign-out is ignored.
  // Signed out: nothing to load; the derived values below report "no workspaces".
  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    client
      .get<Tenant[]>('/api/tenants')
      .then((res) => {
        if (!cancelled) applyTenants(res.data);
      })
      .catch((err) => console.error('Failed to fetch tenants', err))
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [user, applyTenants]);

  // Stable identity, so effects that depend on it do not re-run on every render.
  const setCurrentTenantId = useCallback((id: string) => {
    setCurrentTenantIdState(id);
    localStorage.setItem('tenantId', id);
  }, []);

  /** Append a newly created tenant and immediately switch to it */
  const addTenant = (tenant: Tenant) => {
    setTenants(prev => [...prev, tenant]);
    setCurrentTenantIdState(tenant.id);
    localStorage.setItem('tenantId', tenant.id);
  };

  // Without a user there are no workspaces, whatever was loaded for a previous session.
  const visibleTenants = user ? tenants : [];
  const currentTenant = visibleTenants.find(t => t.id === currentTenantId) || null;

  return (
    <TenantContext.Provider value={{ tenants: visibleTenants, currentTenant, loading: user ? loading : false, setCurrentTenantId, refreshTenants: fetchTenants, addTenant }}>
      {children}
    </TenantContext.Provider>
  );
};

export const useTenant = () => {
  const context = useContext(TenantContext);
  if (context === undefined) {
    throw new Error('useTenant must be used within a TenantProvider');
  }
  return context;
};
