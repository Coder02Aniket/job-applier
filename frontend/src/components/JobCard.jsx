import React from 'react';
import {
  ArrowUpRight,
  Check,
  Doc,
  Mail,
  Pin,
  X,
} from './Icons';
import {
  STATUS_LABEL,
  relativeTime,
  scorePercent,
  scoreTier,
  statusTone,
} from '../lib/format';

const TERMINAL_STATUSES = ['APPROVED', 'APPLIED', 'MANUAL_REQUIRED', 'REPLIED', 'INTERVIEW', 'REJECTED', 'SKIPPED', 'FAILED'];

export default function JobCard({
  item,
  index,
  onViewDiff,
  onOpenOutreach,
  onApprove,
  onSkip,
  onChangeStatus,
  busy,
}) {
  const job = item.job || {};
  const pct = scorePercent(job.score);
  const tier = scoreTier(pct);
  const isPending = item.status === 'PENDING_REVIEW';

  return (
    <article className="card" style={{ '--i': Math.min(index, 8) }}>
      <div>
        <div className="card__top">
          <span className="chip">
            <span className="chip__dot" />
            {job.source || 'SOURCE'}
          </span>
          {job.location && (
            <span className="chip chip--loc">
              <Pin width={12} height={12} />
              {job.location}
            </span>
          )}
          {job.postedAt && (
            <span className="chip chip--loc">Posted {relativeTime(job.postedAt)}</span>
          )}
        </div>

        <h2 className="card__title">
          {job.url ? (
            <a href={job.url} target="_blank" rel="noreferrer">
              {job.title || 'Untitled role'}
            </a>
          ) : (
            job.title || 'Untitled role'
          )}
        </h2>
        <div className="card__company">{job.company || 'Unknown company'}</div>

        {job.description && <p className="card__desc">{job.description}</p>}

        <div className="card__links">
          <button type="button" className="linkbtn linkbtn--ice" onClick={() => onViewDiff(item)}>
            <Doc width={14} height={14} />
            Resume diff
          </button>
          {job.recruiterEmail ? (
            <button type="button" className="linkbtn linkbtn--amber" onClick={() => onOpenOutreach(item)}>
              <Mail width={14} height={14} />
              Outreach draft
            </button>
          ) : (
            <span className="linkbtn" style={{ color: 'var(--dim)', cursor: 'default' }}>
              <Mail width={14} height={14} />
              No recruiter email on post
            </span>
          )}
          {job.url && (
            <a className="linkbtn" href={job.url} target="_blank" rel="noreferrer">
              <ArrowUpRight width={14} height={14} />
              Open posting
            </a>
          )}
        </div>
      </div>

      <div className="card__side">
        <div
          className={`score score--${tier}`}
          style={{ '--p': pct }}
          title={`Match score ${pct}%`}
        >
          <div className="score__inner">
            <div className="score__num">{pct}</div>
            <div className="score__cap">match</div>
          </div>
        </div>

        {isPending ? (
          <div className="card__actions">
            <button
              type="button"
              className="btn btn--ghost btn--sm"
              onClick={() => onSkip(item)}
              disabled={busy}
            >
              <X width={14} height={14} />
              Skip
            </button>
            <button
              type="button"
              className="btn btn--go btn--sm"
              onClick={() => onApprove(item)}
              disabled={busy}
            >
              <Check width={14} height={14} />
              Approve
            </button>
          </div>
        ) : (
          <div className="card__actions" style={{ flexDirection: 'column', alignItems: 'flex-end' }}>
            <span className={`badge badge--${statusTone(item.status)}`}>
              {STATUS_LABEL[item.status] || item.status}
            </span>
            <label className="sr-only" htmlFor={`status-${item.id}`}>
              Change status for {job.title}
            </label>
            <select
              id={`status-${item.id}`}
              className="status-select"
              value={item.status}
              onChange={(e) => onChangeStatus(item, e.target.value)}
              disabled={busy}
            >
              {TERMINAL_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {STATUS_LABEL[s] || s}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>
    </article>
  );
}
