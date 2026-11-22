// Auth-aware API client for the Job Applier backend.
// In local dev the backend permits all requests, so no credentials are sent.
// In production (app.security.enabled=true) the user logs in once and we attach
// an HTTP Basic header stored in sessionStorage for the tab's lifetime.

const BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api';
const STORAGE_KEY = 'jobapplier.auth';

export function setCredentials(username, password) {
  const token = 'Basic ' + btoa(`${username}:${password}`);
  sessionStorage.setItem(STORAGE_KEY, token);
}

export function clearCredentials() {
  sessionStorage.removeItem(STORAGE_KEY);
}

export function hasCredentials() {
  return Boolean(sessionStorage.getItem(STORAGE_KEY));
}

function authHeaders(extra = {}) {
  const token = sessionStorage.getItem(STORAGE_KEY);
  return token ? { ...extra, Authorization: token } : { ...extra };
}

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function request(path, options = {}) {
  const res = await fetch(`${BASE}${path}`, {
    ...options,
    headers: authHeaders(options.headers),
  });

  if (res.status === 401) {
    throw new ApiError('Authentication required', 401);
  }

  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const problem = await res.json();
      message = problem.detail || problem.message || problem.title || message;
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(message, res.status);
  }

  if (res.status === 204) return null;
  const type = res.headers.get('content-type') || '';
  return type.includes('application/json') ? res.json() : res.text();
}

// ---- Endpoints -------------------------------------------------------------
export const getProfile = () => request('/profile');
export const getSummary = () => request('/summary');
export const getQueue = (status = 'PENDING_REVIEW') =>
  request(`/applications?status=${encodeURIComponent(status)}`);
export const approve = (id) => request(`/applications/${id}/approve`, { method: 'POST' });
export const skip = (id) => request(`/applications/${id}/skip`, { method: 'POST' });
export const updateAppStatus = (id, status) =>
  request(`/applications/${id}/status?status=${encodeURIComponent(status)}`, { method: 'POST' });
export const getDrafts = () => request('/outreach');
export const saveDraft = (id, draft) =>
  request(`/outreach/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(draft),
  });
export const approveDraft = (id) =>
  request(`/outreach/${id}/approve`, { method: 'POST' });
export const sendMail = (id, resumePath) =>
  request(`/outreach/${id}/send?resumePath=${encodeURIComponent(resumePath)}`, { method: 'POST' });
export const runPipeline = () => request('/pipeline/run', { method: 'POST' });
