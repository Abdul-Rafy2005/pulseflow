import React from 'react';

/**
 * Top events — simple horizontal progress bars matching the mockup.
 * Props: data — Array of { eventType, count }
 */
export default function TopEventsChart({ data }) {
  const maxCount = data && data.length > 0 ? Math.max(...data.map((d) => d.count), 1) : 1;

  return (
    <div style={{ background: '#0A0D0A', padding: '16px' }}>
      <div style={{ color: '#7A8B7D', fontSize: '11px', letterSpacing: '1px', marginBottom: '10px' }}>
        TOP EVENTS
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        {data && data.length > 0 ? (
          data.map((item) => (
            <div key={item.eventType} style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ color: '#4A5A4C', fontSize: '10px', width: '70px', flexShrink: 0 }}>
                {item.eventType}
              </span>
              <div
                style={{
                  height: '6px',
                  background: '#F2B84B',
                  width: `${(item.count / maxCount) * 100}%`,
                  borderRadius: '2px',
                }}
              />
            </div>
          ))
        ) : (
          <>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <span style={{ color: '#4A5A4C', fontSize: '10px', width: '70px' }}>—</span>
              <div style={{ height: '6px', background: '#1E2A1C', width: '40%', borderRadius: '2px' }} />
            </div>
          </>
        )}
      </div>
    </div>
  );
}
