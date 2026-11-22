import React, { useEffect, useState } from 'react';
import ReviewQueue from './pages/ReviewQueue';
import AuthGate from './components/AuthGate';
import { ToastProvider } from './components/Toast';
import { getProfile } from './api/client';

function Boot() {
  return (
    <div className="boot">
      <div className="boot__ring" aria-hidden="true" />
      <p className="boot__label">Establishing link…</p>
    </div>
  );
}

export default function App() {
  const [phase, setPhase] = useState('boot');

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        await getProfile();
        if (!cancelled) setPhase('ready');
      } catch (err) {
        // 401 => the deployment requires credentials; anything else => let the
        // dashboard surface its own connection errors.
        if (!cancelled) setPhase(err?.status === 401 ? 'auth' : 'ready');
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <ToastProvider>
      {phase === 'boot' && <Boot />}
      {phase === 'auth' && <AuthGate onAuthed={() => setPhase('ready')} />}
      {phase === 'ready' && <ReviewQueue />}
    </ToastProvider>
  );
}
