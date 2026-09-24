import { createContext, useContext, useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import client, { refreshAccessToken } from '../api/client';

interface User {
  id: string;
  email: string;
  name: string;
  createdAt?: string;
}

interface AuthContextType {
  user: User | null;
  loading: boolean;
  login: (token: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Re-fetches the current user from the API (e.g. after a profile update elsewhere). */
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const checkAuth = async () => {
      let token = localStorage.getItem('token');

      // No local access token - this browser might still have a valid "remember me"
      // HttpOnly refresh cookie (e.g. localStorage was cleared but cookies weren't).
      // Try a silent refresh before concluding the user is logged out.
      if (!token) {
        token = await refreshAccessToken();
      }

      if (token) {
        try {
          const res = await client.get('/api/users/me');
          setUser(res.data);
        } catch {
          localStorage.removeItem('token');
        }
      }
      setLoading(false);
    };
    checkAuth();
  }, []);

  const login = async (token: string): Promise<void> => {
    localStorage.setItem('token', token);
    const res = await client.get('/api/users/me');
    setUser(res.data);
  };

  const logout = async (): Promise<void> => {
    try {
      // Best-effort: revokes the "remember me" refresh token server-side and clears its
      // cookie. Local logout must still succeed even if this call fails (e.g. offline).
      await client.post('/api/auth/logout');
    } catch {
      // Ignore - the user is being logged out locally regardless.
    } finally {
      localStorage.removeItem('token');
      setUser(null);
    }
  };

  const refreshUser = async (): Promise<void> => {
    const res = await client.get('/api/users/me');
    setUser(res.data);
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, refreshUser }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
