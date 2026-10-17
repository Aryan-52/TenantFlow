import { createContext, useCallback, useContext, useState, useEffect } from 'react';
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
  /** Set when the session could not be checked (server unreachable, timeout, 5xx) - not a sign-out. */
  startupError: boolean;
  /** Checks the session again after a startup error. */
  retryStartup: () => void;
  login: (token: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Re-fetches the current user from the API (e.g. after a profile update elsewhere). */
  refreshUser: () => Promise<void>;
}

/** A session check never waits longer than this; then the startup error screen appears. */
const STARTUP_TIMEOUT_MS = 15_000;

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  const [startupError, setStartupError] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const checkAuth = async () => {
      let token = localStorage.getItem('token');

      // No local access token - this browser might still have a valid "remember me"
      // HttpOnly refresh cookie (e.g. localStorage was cleared but cookies weren't).
      // Try a silent refresh before concluding the user is logged out.
      if (!token) {
        token = await refreshAccessToken();
      }

      let failed = false;
      let current: User | null = null;
      if (token) {
        try {
          const res = await client.get('/api/users/me', { timeout: STARTUP_TIMEOUT_MS });
          current = res.data;
        } catch (err) {
          const status = (err as { response?: { status?: number } })?.response?.status;
          // Only a rejected session signs the user out. A server that is down or slow is
          // reported as such (and the stored session is kept for the next try).
          if (status === 401 || status === 403) localStorage.removeItem('token');
          else failed = true;
        }
      }
      if (cancelled) return;
      setUser(current);
      setStartupError(failed);
      setLoading(false);
    };
    checkAuth();
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  const retryStartup = useCallback(() => {
    setLoading(true);
    setStartupError(false);
    setAttempt((n) => n + 1);
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
    <AuthContext.Provider value={{ user, loading, startupError, retryStartup, login, logout, refreshUser }}>
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
