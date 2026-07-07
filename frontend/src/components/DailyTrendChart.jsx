import React from 'react';

export default function DailyTrendChart({ data }) {
  // We need to map data array to SVG points: 0,y1 40,y2 ...
  // viewBox="0 0 300 80"
  
  let points = "0,70 40,68 80,55 120,60 160,40 200,45 240,20 280,25 300,10"; // Fallback/Default from mockup
  
  if (data && data.length > 1) {
    const maxEvents = Math.max(...data.map((d) => (d.totalEvents || d.events || 0)), 1); // Avoid div by 0
    // Chart is 300px wide, 80px high
    const xStep = 300 / (data.length - 1);
    const yMax = 70; // Leave 10px padding at top
    
    points = data.map((d, i) => {
      const x = (i * xStep).toFixed(1);
      // y is inverted (0 is top)
      const y = (80 - ((d.totalEvents || d.events || 0) / maxEvents) * yMax).toFixed(1);
      return `${x},${y}`;
    }).join(' ');
  }

  return (
    <div style={{ background: '#0A0D0A', padding: '16px' }}>
      <div style={{ color: '#7A8B7D', fontSize: '11px', letterSpacing: '1px', marginBottom: '10px' }}>
        EVENT VOLUME — 7D
      </div>
      <svg width="100%" height="80" viewBox="0 0 300 80" preserveAspectRatio="none">
        <polyline 
          points={points} 
          fill="none" 
          stroke="#5EEAA0" 
          strokeWidth="2"
        />
      </svg>
    </div>
  );
}
