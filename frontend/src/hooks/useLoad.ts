import { useCallback, useEffect, useState, type DependencyList, type Dispatch, type SetStateAction } from 'react';
import { apiError } from '../api/work';

export interface Loaded<T> {
  data: T | null;
  setData: Dispatch<SetStateAction<T | null>>;
  error: string;
  loading: boolean;
  reload: () => void;
}

/**
 * Loads data for a view and reloads when `deps` change. The previous request is aborted,
 * so a late response for an old workspace, project or filter can never replace newer data.
 * On a failed reload the last good data stays visible next to the error.
 */
export function useLoad<T>(load: (signal: AbortSignal) => Promise<T>, deps: DependencyList, fallbackError = 'Could not load this view.'): Loaded<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [token, setToken] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    load(controller.signal)
      .then((d) => {
        setData(d);
        setError('');
      })
      .catch((err) => {
        if (!controller.signal.aborted) setError(apiError(err, fallbackError).message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, token]);

  const reload = useCallback(() => setToken((n) => n + 1), []);
  return { data, setData, error, loading, reload };
}
