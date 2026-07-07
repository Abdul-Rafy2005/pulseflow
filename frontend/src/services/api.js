/**
 * API service for PulseFlow backend.
 * All calls go through the Vite dev proxy so no absolute URLs needed.
 */

const API_BASE = '';

/**
 * Store and retrieve the JWT token.
 */
export function getToken() {
  return localStorage.getItem('pulseflow_token');
}

export function setToken(token) {
  localStorage.setItem('pulseflow_token', token);
}

export function clearToken() {
  localStorage.removeItem('pulseflow_token');
}

/**
 * Wrapper around fetch that injects the JWT Authorization header.
 */
async function apiFetch(path, options = {}) {
  const token = getToken();
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers,
  });

  if (response.status === 401 || response.status === 403) {
    clearToken();
    window.location.reload();
    throw new Error('Unauthorized');
  }

  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.message || `Request failed: ${response.status}`);
  }

  return response.json();
}

// ─── Auth ───────────────────────────────────────────────

export async function login(identifier, password) {
  const data = await apiFetch('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ identifier, password }),
  });
  setToken(data.token);
  return data;
}

export async function getProfile() {
  return apiFetch('/auth/profile');
}

// ─── Analytics ──────────────────────────────────────────

export async function getRealtimeSnapshot() {
  return apiFetch('/analytics/realtime');
}

export async function getSummary() {
  return apiFetch('/analytics/summary');
}

export async function getDailyStats(days = 30) {
  return apiFetch(`/analytics/daily?days=${days}`);
}

export async function getTopEvents(limit = 10) {
  return apiFetch(`/analytics/top-events?limit=${limit}`);
}

export async function getTopUsers(limit = 10) {
  return apiFetch(`/analytics/top-users?limit=${limit}`);
}

// ─── Events ─────────────────────────────────────────────

/**
 * Fetch paginated, filterable event log.
 * @param {Object} params  - { eventType, status, dateFrom, dateTo, page, size, sort }
 */
export async function getEvents({ eventType, status, dateFrom, dateTo, page = 0, size = 20, sort = 'receivedAt,desc' } = {}) {
  const q = new URLSearchParams();
  if (eventType) q.set('eventType', eventType);
  if (status) q.set('status', status);
  if (dateFrom) q.set('dateFrom', dateFrom);
  if (dateTo) q.set('dateTo', dateTo);
  q.set('page', page);
  q.set('size', size);
  q.set('sort', sort);
  return apiFetch(`/events?${q.toString()}`);
}
