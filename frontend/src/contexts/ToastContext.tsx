import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { CheckCircle2, AlertCircle, X } from 'lucide-react';

type ToastKind = 'success' | 'error';

interface Toast {
  id: number;
  kind: ToastKind;
  message: string;
}

interface ToastApi {
  success: (message: string) => void;
  error: (message: string) => void;
}

const NOOP: ToastApi = { success: () => undefined, error: () => undefined };
const ToastContext = createContext<ToastApi>(NOOP);

const DURATION_MS = 4000;

const ToastItem = ({ toast, onDismiss }: { toast: Toast; onDismiss: (id: number) => void }) => {
  useEffect(() => {
    const timer = window.setTimeout(() => onDismiss(toast.id), DURATION_MS);
    return () => window.clearTimeout(timer);
  }, [toast.id, onDismiss]);

  return (
    <div className={`toast toast-${toast.kind}`} role={toast.kind === 'error' ? 'alert' : 'status'}>
      {toast.kind === 'success' ? <CheckCircle2 size={16} aria-hidden="true" /> : <AlertCircle size={16} aria-hidden="true" />}
      <span className="toast-message">{toast.message}</span>
      <button type="button" className="toast-close" onClick={() => onDismiss(toast.id)} aria-label="Dismiss notification">
        <X size={14} />
      </button>
    </div>
  );
};

/** Brief, non-blocking confirmation after an action (saved, deleted, invited...). */
export const ToastProvider = ({ children }: { children: ReactNode }) => {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => setToasts((prev) => prev.filter((t) => t.id !== id)), []);
  const push = useCallback((kind: ToastKind, message: string) => {
    const id = nextId.current++;
    setToasts((prev) => [...prev.slice(-2), { id, kind, message }]);
  }, []);

  const api = useMemo<ToastApi>(() => ({ success: (m) => push('success', m), error: (m) => push('error', m) }), [push]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-region" aria-live="polite">
        {toasts.map((t) => (
          <ToastItem key={t.id} toast={t} onDismiss={dismiss} />
        ))}
      </div>
    </ToastContext.Provider>
  );
};

/** Outside a provider (e.g. isolated component tests) toasts are silently ignored. */
// eslint-disable-next-line react/only-export-components
export const useToast = () => useContext(ToastContext);
