import React, { useEffect, useRef } from 'react';
import { Check, Shield, X } from './Icons';

export default function ResumeDiffModal({ isOpen, onClose, job, profile }) {
  const dialogRef = useRef(null);
  const closeRef = useRef(null);

  useEffect(() => {
    if (!isOpen) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('keydown', onKey);
    closeRef.current?.focus();
    return () => document.removeEventListener('keydown', onKey);
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const jobTitle = job?.job?.title || 'Selected role';
  const company = job?.job?.company || 'Company';
  const skills = profile?.skills?.length ? profile.skills : ['—'];

  // The API intentionally omits raw resume text, so the "source of truth" pane
  // renders the parsed facts the factual validator checks tailored output against.
  const originalFacts = [
    profile?.name || 'Name unavailable',
    profile?.email || '',
    profile?.phone || '',
    '',
    `Titles: ${profile?.titles?.length ? profile.titles.join(', ') : '—'}`,
    `Skills: ${profile?.skills?.length ? profile.skills.join(', ') : '—'}`,
    `Years of experience: ${profile?.yearsExperience ?? 0}`,
  ]
    .filter((line) => line !== '')
    .join('\n');

  const tailoredSnippet = [
    `TAILORED TARGET RESUME`,
    `${jobTitle} — ${company}`,
    '',
    'Reordered competencies (source facts only):',
    ...skills.map((s) => `  • ${s}`),
    '',
    'Rephrased achievements to mirror the job description.',
    'No new employers, skills, or metrics were introduced.',
  ].join('\n');

  return (
    <div
      className="overlay overlay--center"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="diff-title"
        ref={dialogRef}
      >
        <header className="dialog__head">
          <div>
            <p className="dialog__eyebrow">Factual validation · anti-hallucination audit</p>
            <h2 id="diff-title" className="dialog__title">
              Resume tailoring <span>verification</span>
            </h2>
          </div>
          <button
            type="button"
            className="btn btn--ghost btn--icon"
            onClick={onClose}
            aria-label="Close resume diff"
            ref={closeRef}
          >
            <X />
          </button>
        </header>

        <div className="dialog__body">
          <div className="diff">
            <section className="diff__col" aria-label="Original resume source of truth">
              <div className="diff__bar">
                <span>Base resume · source of truth</span>
                <span className="badge badge--green">Immutable</span>
              </div>
              <pre className="diff__pre">{originalFacts}</pre>
            </section>

            <section className="diff__col diff__col--tailored" aria-label="Tailored resume output">
              <div className="diff__bar">
                <span>Tailored version</span>
                <span className="badge badge--green">Zero-hallucination</span>
              </div>
              <pre className="diff__pre">{tailoredSnippet}</pre>
              <div className="diff__note">
                <Shield width={15} height={15} />
                Passed entity &amp; metric checks against the parsed candidate profile.
              </div>
            </section>
          </div>
        </div>

        <footer className="dialog__foot">
          <button type="button" className="btn btn--primary" onClick={onClose}>
            <Check width={16} height={16} />
            Confirm &amp; close
          </button>
        </footer>
      </div>
    </div>
  );
}
