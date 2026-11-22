import React, { useEffect, useRef, useState } from 'react';
import { Check, Mail, Shield, X } from './Icons';
import { wordCount } from '../lib/format';

export default function OutreachDrawer({
  isOpen,
  onClose,
  draft,
  persisted = false,
  resumePath,
  onSave,
  onApprove,
  onSend,
  sending = false,
  approving = false,
}) {
  const [subject, setSubject] = useState('');
  const [body, setBody] = useState('');
  const [error, setError] = useState('');
  const closeRef = useRef(null);

  useEffect(() => {
    if (!isOpen || !draft) return;
    setSubject(draft.subject || '');
    setBody(draft.body || '');
    setError('');
  }, [isOpen, draft]);

  useEffect(() => {
    if (!isOpen) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    closeRef.current?.focus();
    return () => document.removeEventListener('keydown', onKey);
  }, [isOpen, onClose]);

  if (!isOpen || !draft) return null;

  const recipient = draft.recipient || '';
  const words = wordCount(body);
  const dirty = subject !== (draft.subject || '') || body !== (draft.body || '');
  const approved = draft.status === 'APPROVED';
  const canSend = persisted && Boolean(recipient) && approved && !sending;

  async function handleSave() {
    setError('');
    try {
      await onSave({ ...draft, subject, body });
    } catch (err) {
      setError(err.message || 'Could not save the draft.');
    }
  }

  async function handleApprove() {
    setError('');
    try {
      await onApprove({ ...draft, subject, body });
    } catch (err) {
      setError(err.message || 'Could not approve the draft.');
    }
  }

  async function handleSend() {
    setError('');
    const ok = window.confirm(
      `Send this outreach email to ${recipient || 'the recruiter'}?\n\nThis dispatches immediately from your configured SMTP account.`,
    );
    if (!ok) return;
    try {
      await onSend(draft, resumePath);
    } catch (err) {
      setError(err.message || 'Send was blocked or failed.');
    }
  }

  return (
    <div
      className="overlay overlay--right"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <aside className="drawer" role="dialog" aria-modal="true" aria-labelledby="outreach-title">
        <header className="drawer__head">
          <div>
            <p className="dialog__eyebrow" style={{ color: 'var(--amber)' }}>
              Approval gate · human in the loop
            </p>
            <h2 id="outreach-title" className="dialog__title">
              Recruiter outreach
            </h2>
          </div>
          <button
            type="button"
            className="btn btn--ghost btn--icon"
            onClick={onClose}
            aria-label="Close outreach drawer"
            ref={closeRef}
          >
            <X />
          </button>
        </header>

        <div className="drawer__body">
          {error && (
            <div className="note note--red" role="alert">
              <X width={15} height={15} />
              <span>{error}</span>
            </div>
          )}

          {!persisted && (
            <div className="note note--amber">
              <Shield width={15} height={15} />
              <span>
                No persisted draft exists for this role yet. Run the pipeline to generate an
                outreach draft, or approve the application first. Sending is disabled until a draft
                is on record.
              </span>
            </div>
          )}

          <div className="field">
            <label className="field__label" htmlFor="outreach-recipient">
              Verified recipient
              <span className="field__hint">never guessed</span>
            </label>
            <input
              id="outreach-recipient"
              className="input"
              readOnly
              value={recipient || 'No email found on the posting'}
            />
          </div>

          <div className="field">
            <label className="field__label" htmlFor="outreach-subject">
              Subject
            </label>
            <input
              id="outreach-subject"
              className="input"
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              disabled={!persisted}
            />
          </div>

          <div className="field" style={{ flex: 1 }}>
            <label className="field__label" htmlFor="outreach-body">
              Email body
              <span className="field__hint">{words} words · target &lt; 120</span>
            </label>
            <textarea
              id="outreach-body"
              className="textarea textarea--mono"
              value={body}
              onChange={(e) => setBody(e.target.value)}
              disabled={!persisted}
            />
          </div>

          <div className={`note ${approved ? 'note--green' : 'note--amber'}`}>
            <Shield width={15} height={15} />
            <span>
              <strong>{approved ? 'Approved for send.' : 'Approval required.'}</strong>{' '}
              {approved
                ? 'This draft has been explicitly approved. Sending is still a separate, deliberate step.'
                : 'Approve the draft first, then send. Nothing is dispatched automatically.'}
            </span>
          </div>
        </div>

        <footer className="drawer__foot">
          <button
            type="button"
            className="btn btn--ghost"
            onClick={handleSave}
            disabled={!persisted || !dirty || approved}
          >
            <Check width={16} height={16} />
            Save draft
          </button>
          {!approved ? (
            <button
              type="button"
              className="btn btn--primary"
              onClick={handleApprove}
              disabled={!persisted || !recipient || approving}
            >
              <Shield width={16} height={16} />
              {approving ? 'Approving…' : 'Approve draft'}
            </button>
          ) : (
            <button
              type="button"
              className="btn btn--go"
              onClick={handleSend}
              disabled={!canSend}
            >
              <Mail width={16} height={16} />
              {sending ? 'Sending…' : 'Send approved mail'}
            </button>
          )}
        </footer>
      </aside>
    </div>
  );
}
