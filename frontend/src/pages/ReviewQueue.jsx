import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  approve,
  approveDraft,
  getDrafts,
  getProfile,
  getQueue,
  getSummary,
  runPipeline,
  saveDraft,
  sendMail,
  skip,
  updateAppStatus,
} from '../api/client';
import { initials, relativeTime } from '../lib/format';
import {
  Bolt,
  Inbox,
  Layers,
  Radar,
  Search,
  Shield,
} from '../components/Icons';
import JobCard from '../components/JobCard';
import ResumeDiffModal from '../components/ResumeDiffModal';
import OutreachDrawer from '../components/OutreachDrawer';
import { useToast } from '../components/Toast';

const TABS = [
  { id: 'PENDING_REVIEW', label: 'Pending' },
  { id: 'APPROVED', label: 'Approved' },
  { id: 'APPLIED', label: 'Applied' },
  { id: 'MANUAL_REQUIRED', label: 'Manual' },
  { id: 'REPLIED', label: 'Replied' },
  { id: 'SKIPPED', label: 'Archive' },
];

function Clock() {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(id);
  }, []);
  return (
    <span className="clock" aria-hidden="true">
      {now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
    </span>
  );
}

export default function ReviewQueue() {
  const toast = useToast();

  const [profile, setProfile] = useState(null);
  const [summary, setSummary] = useState(null);
  const [drafts, setDrafts] = useState([]);
  const [items, setItems] = useState([]);
  const [activeTab, setActiveTab] = useState('PENDING_REVIEW');
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState(null);
  const [pipelineRunning, setPipelineRunning] = useState(false);
  const [lastSync, setLastSync] = useState(null);

  const [diff, setDiff] = useState({ open: false, item: null });
  const [outreach, setOutreach] = useState({
    open: false,
    draft: null,
    persisted: false,
    resumePath: '',
  });
  const [sending, setSending] = useState(false);
  const [approving, setApproving] = useState(false);

  const loadSummary = useCallback(async () => {
    try {
      setSummary(await getSummary());
    } catch {
      /* non-fatal */
    }
  }, []);

  const loadDrafts = useCallback(async () => {
    try {
      const data = await getDrafts();
      setDrafts(Array.isArray(data) ? data : []);
    } catch {
      setDrafts([]);
    }
  }, []);

  const loadQueue = useCallback(async (status) => {
    setLoading(true);
    try {
      const data = await getQueue(status);
      setItems(Array.isArray(data) ? data : []);
      setLastSync(new Date());
    } catch (err) {
      toast.error(err.message || 'Could not load the queue.');
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [toast]);

  const refresh = useCallback(async (status = activeTab) => {
    await Promise.all([loadSummary(), loadDrafts(), loadQueue(status)]);
  }, [activeTab, loadDrafts, loadQueue, loadSummary]);

  useEffect(() => {
    (async () => {
      try {
        setProfile(await getProfile());
      } catch {
        /* profile is optional chrome */
      }
      await Promise.all([loadSummary(), loadDrafts()]);
    })();
  }, [loadDrafts, loadSummary]);

  useEffect(() => {
    loadQueue(activeTab);
  }, [activeTab, loadQueue]);

  const counts = useMemo(() => summary || {}, [summary]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return items;
    return items.filter((a) => {
      const j = a.job || {};
      return [j.title, j.company, j.location].some((v) =>
        (v || '').toLowerCase().includes(q),
      );
    });
  }, [items, query]);

  const pendingCount = counts.PENDING_REVIEW ?? 0;

  async function withBusy(id, fn, successMsg) {
    setBusyId(id);
    try {
      await fn();
      if (successMsg) toast.success(successMsg);
      await refresh();
    } catch (err) {
      toast.error(err.message || 'Action failed.');
    } finally {
      setBusyId(null);
    }
  }

  const handleApprove = (item) =>
    withBusy(item.id, () => approve(item.id), 'Application approved for dispatch.');
  const handleSkip = (item) =>
    withBusy(item.id, () => skip(item.id), 'Application skipped.');
  const handleStatus = (item, status) =>
    withBusy(item.id, () => updateAppStatus(item.id, status), `Status set to ${status}.`);

  async function handleRunPipeline() {
    setPipelineRunning(true);
    toast.info('Ingestion pipeline running — this can take a moment.');
    try {
      await runPipeline();
      toast.success('Pipeline finished. Queue refreshed.');
      await refresh();
    } catch (err) {
      toast.error(err.message || 'Pipeline run failed.');
    } finally {
      setPipelineRunning(false);
    }
  }

  function defaultBody(item) {
    const skills = profile?.skills?.slice(0, 3).join(', ') || 'software engineering';
    return `Hello,\n\nI am writing to express my interest in the ${item.job?.title || 'role'} position at ${
      item.job?.company || 'your team'
    }. My background in ${skills} aligns closely with your requirements. I have attached my resume for your review.\n\nBest regards,\n${
      profile?.name || ''
    }`;
  }

  function openOutreach(item) {
    const match = drafts.find((d) => d.job?.id === item.job?.id);
    const provisional = {
      id: null,
      job: item.job,
      recipient: item.job?.recruiterEmail || '',
      subject: `Application: ${item.job?.title || 'Role'} — ${profile?.name || 'Candidate'}`,
      body: defaultBody(item),
      status: 'DRAFT',
    };
    setOutreach({
      open: true,
      draft: match || provisional,
      persisted: Boolean(match),
      resumePath:
        item.resumePath || `data/tailored/resume_${item.job?.id ?? 'draft'}.docx`,
    });
  }

  async function handleSaveDraft(payload) {
    await saveDraft(outreach.draft.id, payload);
    toast.success('Draft saved.');
    await loadDrafts();
  }

  async function handleApproveDraft(payload) {
    setApproving(true);
    try {
      // Persist any edits first, then mark approved.
      await saveDraft(outreach.draft.id, payload);
      const updated = await approveDraft(outreach.draft.id);
      setOutreach((prev) => ({ ...prev, draft: { ...prev.draft, ...updated } }));
      toast.success('Draft approved. Sending is now unlocked.');
      await loadDrafts();
    } finally {
      setApproving(false);
    }
  }

  async function handleSend(draft, resumePath) {
    setSending(true);
    try {
      await sendMail(draft.id, resumePath);
      toast.success('Outreach email dispatched.');
      setOutreach({ open: false, draft: null, persisted: false, resumePath: '' });
      await refresh();
    } finally {
      setSending(false);
    }
  }

  const operatorName = profile?.name || 'Unassigned operator';

  return (
    <div className="shell">
      <header className="masthead">
        <div className="brand">
          <span className="brand__mark" aria-hidden="true">
            <Radar width={20} height={20} />
          </span>
          <div>
            <div className="brand__name">JOB APPLIER</div>
            <div className="brand__sub">Mission Control</div>
          </div>
        </div>
        <div className="masthead__meta">
          <Clock />
          <span className="status-pill">
            <span className={`led ${pipelineRunning ? 'led--amber' : ''}`} />
            {pipelineRunning ? 'Pipeline active' : 'Systems nominal'}
          </span>
        </div>
      </header>

      <div className="deck">
        {/* ---------- Rail ---------- */}
        <aside className="rail" aria-label="Operator and controls">
          <section className="panel">
            <div className="panel__head">
              <span className="panel__title">Operator</span>
              <span className="badge badge--green">online</span>
            </div>
            <div className="panel__body">
              <div className="operator">
                <span className="avatar" aria-hidden="true">
                  {initials(profile?.name)}
                </span>
                <div style={{ minWidth: 0 }}>
                  <div className="operator__name">{operatorName}</div>
                  <div className="operator__mail">{profile?.email || 'no email on file'}</div>
                </div>
              </div>
              {profile?.skills?.length > 0 && (
                <div className="card__top" style={{ marginTop: '0.85rem', marginBottom: 0 }}>
                  {profile.skills.slice(0, 4).map((s) => (
                    <span key={s} className="chip chip--loc">
                      {s}
                    </span>
                  ))}
                </div>
              )}
            </div>
          </section>

          <section className="panel">
            <div className="panel__head">
              <span className="panel__title">Queue telemetry</span>
              <Layers width={14} height={14} style={{ color: 'var(--dim)' }} />
            </div>
            <div className="panel__body">
              <div className="stats">
                <div className="stat">
                  <div className="stat__val stat__val--amber">{pendingCount}</div>
                  <div className="stat__label">Pending</div>
                </div>
                <div className="stat">
                  <div className="stat__val stat__val--ice">{counts.APPROVED ?? 0}</div>
                  <div className="stat__label">Approved</div>
                </div>
                <div className="stat">
                  <div className="stat__val stat__val--green">{counts.APPLIED ?? 0}</div>
                  <div className="stat__label">Applied</div>
                </div>
                <div className="stat">
                  <div className="stat__val" style={{ color: 'var(--red)' }}>
                    {counts.MANUAL_REQUIRED ?? 0}
                  </div>
                  <div className="stat__label">Manual</div>
                </div>
              </div>
            </div>
          </section>

          <section className="panel">
            <div className="panel__head">
              <span className="panel__title">Ingestion</span>
              <Bolt width={14} height={14} style={{ color: 'var(--amber)' }} />
            </div>
            <div className="panel__body">
              <p className="pipeline__copy">
                Pull fresh roles from Gmail alerts and portals, rank them against the resume, then
                stage tailored drafts for review.
              </p>
              <button
                type="button"
                className="btn btn--primary pipeline__btn"
                onClick={handleRunPipeline}
                disabled={pipelineRunning}
              >
                <Bolt width={16} height={16} />
                {pipelineRunning ? 'Running pipeline…' : 'Run pipeline'}
              </button>
              <p className="panel__title" style={{ marginTop: '0.7rem', textAlign: 'center' }}>
                {lastSync ? `Synced ${relativeTime(lastSync)}` : 'Not synced yet'}
              </p>
            </div>
          </section>

          <div className="guardrail">
            <Shield width={16} height={16} />
            <span>
              Every application and email waits for your explicit approval. Nothing auto-sends.
            </span>
          </div>
        </aside>

        {/* ---------- Main ---------- */}
        <main className="main">
          <div className="main__head">
            <div>
              <p className="eyebrow">Review operations</p>
              <h1 className="main__title">
                Application queue <span>/ {TABS.find((t) => t.id === activeTab)?.label}</span>
              </h1>
            </div>
          </div>

          <div className="toolbar">
            <div className="tabs" role="group" aria-label="Filter by application status">
              {TABS.map((tab) => (
                <button
                  key={tab.id}
                  type="button"
                  aria-pressed={activeTab === tab.id}
                  className="tab"
                  onClick={() => setActiveTab(tab.id)}
                >
                  {tab.label}
                  <span className="tab__count">{counts[tab.id] ?? 0}</span>
                </button>
              ))}
            </div>
            <div className="search">
              <Search width={16} height={16} />
              <label className="sr-only" htmlFor="queue-search">
                Search the queue
              </label>
              <input
                id="queue-search"
                type="search"
                placeholder="Search roles, companies, locations…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </div>
          </div>

          <section
            id="queue-panel"
            aria-label={`${TABS.find((t) => t.id === activeTab)?.label} applications`}
            className="stream"
          >
            {loading ? (
              <>
                <div className="skeleton" />
                <div className="skeleton" />
                <div className="skeleton" />
              </>
            ) : filtered.length === 0 ? (
              <div className="state">
                <span className="state__glyph" aria-hidden="true">
                  <Inbox width={26} height={26} />
                </span>
                <h2 className="state__title">No applications here</h2>
                <p className="state__copy">
                  {query
                    ? 'Nothing matches your search in this category. Clear the search or switch tabs.'
                    : 'This lane is clear. Run the ingestion pipeline to fetch and rank fresh roles for review.'}
                </p>
                {!query && (
                  <button
                    type="button"
                    className="btn btn--primary"
                    onClick={handleRunPipeline}
                    disabled={pipelineRunning}
                  >
                    <Bolt width={16} height={16} />
                    Trigger pipeline
                  </button>
                )}
              </div>
            ) : (
              filtered.map((item, index) => (
                <JobCard
                  key={item.id}
                  item={item}
                  index={index}
                  busy={busyId === item.id}
                  onViewDiff={(it) => setDiff({ open: true, item: it })}
                  onOpenOutreach={openOutreach}
                  onApprove={handleApprove}
                  onSkip={handleSkip}
                  onChangeStatus={handleStatus}
                />
              ))
            )}
          </section>
        </main>
      </div>

      <ResumeDiffModal
        isOpen={diff.open}
        onClose={() => setDiff({ open: false, item: null })}
        job={diff.item}
        profile={profile}
      />

      <OutreachDrawer
        isOpen={outreach.open}
        onClose={() => setOutreach({ open: false, draft: null, persisted: false, resumePath: '' })}
        draft={outreach.draft}
        persisted={outreach.persisted}
        resumePath={outreach.resumePath}
        sending={sending}
        approving={approving}
        onSave={handleSaveDraft}
        onApprove={handleApproveDraft}
        onSend={handleSend}
      />
    </div>
  );
}
