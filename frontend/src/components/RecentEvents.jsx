import React from 'react';

/**
 * Live event tail — matches the mockup's minimal text styling.
 * Row format: HH:MM:SS  TYPE  user_X · source
 */
export default function RecentEvents({ events }) {
  function formatTime(ts) {
    if (!ts) return '—';
    try {
      const date = new Date(ts);
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false });
    } catch {
      return ts;
    }
  }

  // Color by event type — SEARCH, PURCHASE, ERROR get amber, rest get green
  function getTypeColor(type) {
    const amberTypes = ['SEARCH', 'PURCHASE', 'ERROR', 'CUSTOM'];
    return amberTypes.includes(type) ? '#F2B84B' : '#5EEAA0';
  }

  return (
    <div style={{ padding: '16px' }}>
      <div style={{ color: '#7A8B7D', fontSize: '11px', letterSpacing: '1px', marginBottom: '8px' }}>
        RECENT EVENTS <span style={{ color: '#4A5A4C' }}>— live tail</span>
      </div>
      <div style={{ fontSize: '12px', lineHeight: '1.9' }}>
        {events.length === 0 && (
          <div style={{ color: '#4A5A4C' }}>Waiting for events…</div>
        )}
        {events.slice(0, 15).map((event, index) => (
          <div key={event.eventId || index}>
            <span style={{ color: '#4A5A4C' }}>
              {formatTime(event.processedAt || event.receivedAt)}
            </span>{' '}
            <span style={{ color: getTypeColor(event.eventType) }}>
              {event.eventType}
            </span>{' '}
            <span style={{ color: '#7A8B7D' }}>
              {event.userId ? `user_${event.userId}` : '—'} · {event.source || '—'}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
