import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';

export type ThemePreference = 'light' | 'dark' | 'system';

interface ThemeContextValue {
  preference: ThemePreference;
  /** The theme actually shown (system resolved). */
  resolved: 'light' | 'dark';
  setPreference: (pref: ThemePreference) => void;
}

const STORAGE_KEY = 'tf.theme';

const systemPrefersDark = () =>
  typeof window !== 'undefined' && !!window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches;

const readPreference = (): ThemePreference => {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    return saved === 'light' || saved === 'dark' ? saved : 'system';
  } catch {
    return 'system';
  }
};

const ThemeContext = createContext<ThemeContextValue>({
  preference: 'system',
  resolved: 'light',
  setPreference: () => undefined,
});

/**
 * Light / dark / follow-the-system theme. The resolved theme is written to
 * <html data-theme>, which the design tokens in index.css key off. index.html applies the
 * saved theme before first paint, so this only keeps it in sync afterwards.
 */
export const ThemeProvider = ({ children }: { children: ReactNode }) => {
  const [preference, setPreferenceState] = useState<ThemePreference>(readPreference);
  const [systemDark, setSystemDark] = useState(systemPrefersDark);

  useEffect(() => {
    if (!window.matchMedia) return;
    const query = window.matchMedia('(prefers-color-scheme: dark)');
    const onChange = (e: MediaQueryListEvent) => setSystemDark(e.matches);
    query.addEventListener?.('change', onChange);
    return () => query.removeEventListener?.('change', onChange);
  }, []);

  const resolved: 'light' | 'dark' = preference === 'system' ? (systemDark ? 'dark' : 'light') : preference;

  useEffect(() => {
    document.documentElement.dataset.theme = resolved;
  }, [resolved]);

  const setPreference = useCallback((pref: ThemePreference) => {
    setPreferenceState(pref);
    try {
      if (pref === 'system') localStorage.removeItem(STORAGE_KEY);
      else localStorage.setItem(STORAGE_KEY, pref);
    } catch {
      // Storage unavailable (private mode): the choice lasts for this session only.
    }
  }, []);

  return <ThemeContext.Provider value={{ preference, resolved, setPreference }}>{children}</ThemeContext.Provider>;
};

// eslint-disable-next-line react/only-export-components
export const useTheme = () => useContext(ThemeContext);
