import React, { createContext, useCallback, useContext, useMemo, useRef, useState } from 'react';
import { Check, X } from './Icons';

const ToastContext = createContext(null);

const ICONS = {
  success: Check,
  error: X,
  info: Check,
};

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const idRef = useRef(0);

  const dismiss = useCallback((id) => {
    setToasts((list) => list.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    (message, type = 'info', timeout = 4200) => {
      const id = ++idRef.current;
      setToasts((list) => [...list, { id, message, type }]);
      if (timeout > 0) {
        window.setTimeout(() => dismiss(id), timeout);
      }
      return id;
    },
    [dismiss],
  );

  // Stable identity so consumers can safely use it in effect dependency arrays.
  const api = useMemo(
    () => ({
      success: (m, t) => push(m, 'success', t),
      error: (m, t) => push(m, 'error', t),
      info: (m, t) => push(m, 'info', t),
      dismiss,
    }),
    [push, dismiss],
  );

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toasts" role="status" aria-live="polite">
        {toasts.map((t) => {
          const Icon = ICONS[t.type] || Check;
          return (
            <div key={t.id} className={`toast toast--${t.type}`}>
              <Icon width={16} height={16} />
              <span style={{ flex: 1 }}>{t.message}</span>
              <button
                type="button"
                className="linkbtn"
                onClick={() => dismiss(t.id)}
                aria-label="Dismiss notification"
                style={{ padding: 0, color: 'var(--dim)' }}
              >
                <X width={14} height={14} />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within ToastProvider');
  return ctx;
}
