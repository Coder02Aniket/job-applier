import React, { useState } from 'react';
import { getProfile, setCredentials, clearCredentials } from '../api/client';
import { Lock, Radar } from './Icons';

export default function AuthGate({ onAuthed }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      setCredentials(username.trim(), password);
      await getProfile();
      onAuthed();
    } catch (err) {
      clearCredentials();
      setError(
        err?.status === 401
          ? 'Those credentials were rejected. Check your APP_USERNAME / APP_PASSWORD.'
          : 'Could not reach the backend. Is it running on port 8080?',
      );
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth">
      <section className="auth__card" aria-labelledby="auth-title">
        <div className="auth__mark" aria-hidden="true">
          <Radar width={22} height={22} />
        </div>
        <p className="eyebrow">Secure access</p>
        <h1 id="auth-title" className="auth__title">
          Mission Control
        </h1>
        <p className="auth__copy">
          This deployment enforces single-user basic auth. Enter the credentials configured for the
          backend to open the review deck.
        </p>

        <form className="auth__form" onSubmit={handleSubmit}>
          <div className="field">
            <label className="field__label" htmlFor="auth-user">
              Operator ID
            </label>
            <input
              id="auth-user"
              className="input"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>
          <div className="field">
            <label className="field__label" htmlFor="auth-pass">
              Passphrase
            </label>
            <input
              id="auth-pass"
              className="input"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          {error && (
            <p className="auth__error" role="alert">
              {error}
            </p>
          )}
          <button className="btn btn--primary" type="submit" disabled={busy}>
            <Lock width={16} height={16} />
            {busy ? 'Verifying…' : 'Authenticate'}
          </button>
        </form>
      </section>
    </main>
  );
}
