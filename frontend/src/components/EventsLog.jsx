import React, { useState, useEffect, useCallback } from 'react';
import { getEvents } from '../services/api.js';

const EVENT_TYPES = [
  '', 'PAGE_VIEW', 'CLICK', 'LOGIN', 'LOGOUT', 'PURCHASE',
  'VIDEO_PLAY', 'VIDEO_PAUSE', 'SEARCH', 'ERROR', 'CUSTOM',
];
const STATUSES = ['', 'PENDING', 'PROCESSED', 'FAILED'];
const PAGE_SIZES = [10, 20, 50];

function statusBadge(status) {
  const map = {
    PROCESSED: { bg: 'rgba(16,185,129,0.15)', color: '#10b981', label: 'Processed' },
    PENDING:   { bg: 'rgba(245,158,11,0.15)', color: '#f59e0b', label: 'Pending'   },
    FAILED:    { bg: 'rgba(239,68,68,0.15)',  color: '#ef4444', label: 'Failed'    },
  };
  const s = map[status] || { bg: 'rgba(156,163,175,0.15)', color: '#9ca3af', label: status };
  return (
    <span style={{
      display: 'inline-block',
      padding: '2px 10px',
      borderRadius: '12px',
      fontSize: '0.73rem',
      fontWeight: 600,
      letterSpacing: '0.03em',
      background: s.bg,
      color: s.color,
    }}>{s.label}</span>
  );
}

function formatDate(iso) {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString(undefined, {
      month: 'short', day: 'numeric',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    });
  } catch { return iso; }
}

export default function EventsLog() {
  const [filters, setFilters] = useState({
    eventType: '',
    status: '',
    dateFrom: '',
    dateTo: '',
  });
  const [page, setPage]     = useState(0);
  const [size, setSize]     = useState(20);

  const [data, setData]     = useState(null);   // Spring Page<EventResponse>
  const [loading, setLoading] = useState(false);
  const [error, setError]   = useState(null);

  const fetchPage = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await getEvents({
        eventType: filters.eventType || undefined,
        status:    filters.status    || undefined,
        dateFrom:  filters.dateFrom  ? filters.dateFrom + ':00' : undefined,
        dateTo:    filters.dateTo    ? filters.dateTo   + ':00' : undefined,
        page,
        size,
        sort: 'receivedAt,desc',
      });
      setData(result);
    } catch (err) {
      setError(err.message || 'Failed to load events');
    } finally {
      setLoading(false);
    }
  }, [filters, page, size]);

  useEffect(() => { fetchPage(); }, [fetchPage]);

  function handleFilterChange(field, value) {
    setFilters((f) => ({ ...f, [field]: value }));
    setPage(0);
  }

  const totalPages = data ? data.totalPages : 0;
  const rows = data ? data.content : [];

  return (
    <section className="events-log-section" id="events-log">
      <div className="events-log-header">
        <h2 className="events-log-title">Event Log</h2>
        <button className="events-log-refresh" onClick={fetchPage} title="Refresh" aria-label="Refresh events">
          ↻
        </button>
      </div>

      {/* ── Filters ── */}
      <div className="events-filters" id="events-filters">
        <div className="filter-group">
          <label className="filter-label" htmlFor="filter-event-type">Event Type</label>
          <select
            id="filter-event-type"
            className="filter-select"
            value={filters.eventType}
            onChange={(e) => handleFilterChange('eventType', e.target.value)}
          >
            {EVENT_TYPES.map((t) => (
              <option key={t} value={t}>{t || 'All types'}</option>
            ))}
          </select>
        </div>

        <div className="filter-group">
          <label className="filter-label" htmlFor="filter-status">Status</label>
          <select
            id="filter-status"
            className="filter-select"
            value={filters.status}
            onChange={(e) => handleFilterChange('status', e.target.value)}
          >
            {STATUSES.map((s) => (
              <option key={s} value={s}>{s || 'All statuses'}</option>
            ))}
          </select>
        </div>

        <div className="filter-group">
          <label className="filter-label" htmlFor="filter-date-from">From</label>
          <input
            id="filter-date-from"
            type="datetime-local"
            className="filter-input"
            value={filters.dateFrom}
            onChange={(e) => handleFilterChange('dateFrom', e.target.value)}
          />
        </div>

        <div className="filter-group">
          <label className="filter-label" htmlFor="filter-date-to">To</label>
          <input
            id="filter-date-to"
            type="datetime-local"
            className="filter-input"
            value={filters.dateTo}
            onChange={(e) => handleFilterChange('dateTo', e.target.value)}
          />
        </div>

        <div className="filter-group">
          <label className="filter-label" htmlFor="filter-page-size">Per page</label>
          <select
            id="filter-page-size"
            className="filter-select filter-select--sm"
            value={size}
            onChange={(e) => { setSize(Number(e.target.value)); setPage(0); }}
          >
            {PAGE_SIZES.map((n) => (
              <option key={n} value={n}>{n}</option>
            ))}
          </select>
        </div>

        <button
          className="filter-clear-btn"
          id="filter-clear-btn"
          onClick={() => { setFilters({ eventType: '', status: '', dateFrom: '', dateTo: '' }); setPage(0); }}
        >
          Clear
        </button>
      </div>

      {/* ── Table ── */}
      <div className="events-table-wrapper">
        {error && (
          <div className="events-error" role="alert">{error}</div>
        )}

        {loading && (
          <div className="events-loading">
            <div className="events-loading-spinner" />
            <span>Loading…</span>
          </div>
        )}

        {!loading && !error && (
          <>
            <table className="events-table" id="events-table">
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Type</th>
                  <th>User</th>
                  <th>Source</th>
                  <th>Status</th>
                  <th>Received At</th>
                  <th>Processed At</th>
                </tr>
              </thead>
              <tbody>
                {rows.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="events-empty">No events match the current filters.</td>
                  </tr>
                ) : (
                  rows.map((e) => (
                    <tr key={e.id} className="events-row">
                      <td className="events-cell--id">#{e.id}</td>
                      <td>
                        <span className="event-type-chip">{e.eventType}</span>
                      </td>
                      <td>{e.userId ?? '—'}</td>
                      <td>{e.source ?? '—'}</td>
                      <td>{statusBadge(e.status)}</td>
                      <td className="events-cell--date">{formatDate(e.receivedAt)}</td>
                      <td className="events-cell--date">{formatDate(e.processedAt)}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>

            {/* ── Pagination ── */}
            {totalPages > 1 && (
              <div className="events-pagination" id="events-pagination">
                <button
                  className="pagination-btn"
                  id="pagination-prev"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  ← Prev
                </button>

                <div className="pagination-pages">
                  {Array.from({ length: Math.min(totalPages, 7) }, (_, i) => {
                    // Window of 7 pages centered on current page
                    let startPage = Math.max(0, Math.min(page - 3, totalPages - 7));
                    const pageNum = startPage + i;
                    return (
                      <button
                        key={pageNum}
                        className={`pagination-page-btn${page === pageNum ? ' active' : ''}`}
                        onClick={() => setPage(pageNum)}
                        aria-current={page === pageNum ? 'page' : undefined}
                        id={`pagination-page-${pageNum}`}
                      >
                        {pageNum + 1}
                      </button>
                    );
                  })}
                </div>

                <button
                  className="pagination-btn"
                  id="pagination-next"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                >
                  Next →
                </button>

                <span className="pagination-info">
                  Page {page + 1} of {totalPages}
                  {data && <> · {data.totalElements} total</>}
                </span>
              </div>
            )}
          </>
        )}
      </div>
    </section>
  );
}
