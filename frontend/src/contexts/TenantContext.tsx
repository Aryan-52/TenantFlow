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

  const fetchTenants = useCallback(async () => {
    if (!user) {
      setTenants([]);
      setLoading(false);
      return;
    }
    try {
      const res = await client.get('/api/tenants');
      const data: Tenant[] = res.data;
      setTenants(data);

      // If we have tenants but no current or current is invalid, pick the first
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
    } catch (err) {
      console.error('Failed to fetch tenants', err);
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    fetchTenants();
  }, [fetchTenants]);

  const setCurrentTenantId = (id: string) => {
    setCurrentTenantIdState(id);
    localStorage.setItem('tenantId', id);
  };

  /** Append a newly created tenant and immediately switch to it */
  const addTenant = (tenant: Tenant) => {
    setTenants(prev => [...prev, tenant]);
    setCurrentTenantIdState(tenant.id);
    localStorage.setItem('tenantId', tenant.id);
  };

  const currentTenant = tenants.find(t => t.id === currentTenantId) || null;

  return (
    <TenantContext.Provider value={{ tenants, currentTenant, loading, setCurrentTenantId, refreshTenants: fetchTenants, addTenant }}>
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
