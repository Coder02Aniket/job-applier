// Small formatting helpers shared across the dashboard.

export function initials(name) {
  if (!name) return '··';
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase() || '')
    .join('');
}

export function scorePercent(score) {
  const n = Number(score) || 0;
  return Math.max(0, Math.min(100, Math.round(n * 100)));
}

export function scoreTier(pct) {
  if (pct >= 70) return 'high';
  if (pct >= 40) return 'mid';
  return 'low';
}

export function relativeTime(value) {
  if (!value) return '—';
  const then = new Date(value).getTime();
  if (Number.isNaN(then)) return '—';
  const diff = Date.now() - then;
  const mins = Math.round(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days < 30) return `${days}d ago`;
  const months = Math.round(days / 30);
  return `${months}mo ago`;
}

export function wordCount(text) {
  if (!text) return 0;
  return text.trim().split(/\s+/).filter(Boolean).length;
}

export const STATUS_LABEL = {
  PENDING_REVIEW: 'Pending',
  APPROVED: 'Approved',
  APPLIED: 'Applied',
  MANUAL_REQUIRED: 'Manual',
  REPLIED: 'Replied',
  INTERVIEW: 'Interview',
  REJECTED: 'Rejected',
  SKIPPED: 'Skipped',
  FAILED: 'Failed',
};

export function statusTone(status) {
  switch (status) {
    case 'APPLIED':
    case 'INTERVIEW':
      return 'green';
    case 'APPROVED':
    case 'MANUAL_REQUIRED':
    case 'REPLIED':
      return 'amber';
    case 'FAILED':
    case 'REJECTED':
      return 'red';
    default:
      return 'ice';
  }
}
