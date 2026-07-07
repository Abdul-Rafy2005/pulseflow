import React, { useEffect, useRef } from 'react';

export default function HeroStrip({ stats, latestEventTime }) {
  // Use display values that mimic the mockup structure
  return (
    <div style={{ padding: '20px 16px 8px', position: 'relative' }}>
      <CanvasWaveform active={!!latestEventTime} />
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '4px' }}>
        <div>
          <div style={{ color: '#4A5A4C', fontSize: '10px', letterSpacing: '1px' }}>TODAY</div>
          <div style={{ color: '#5EEAA0', fontSize: '22px', fontWeight: 700 }}>{stats.todayEvents}</div>
        </div>
        <div>
          <div style={{ color: '#4A5A4C', fontSize: '10px', letterSpacing: '1px' }}>ACTIVE USERS</div>
          <div style={{ color: '#E8F5E9', fontSize: '22px', fontWeight: 700 }}>{stats.todayActiveUsers}</div>
        </div>
        <div>
          <div style={{ color: '#4A5A4C', fontSize: '10px', letterSpacing: '1px' }}>QUEUE</div>
          <div style={{ color: '#E8F5E9', fontSize: '22px', fontWeight: 700 }}>{stats.queueSize}</div>
        </div>
        <div>
          <div style={{ color: '#4A5A4C', fontSize: '10px', letterSpacing: '1px' }}>RATE</div>
          <div style={{ color: '#F2B84B', fontSize: '22px', fontWeight: 700 }}>{stats.processingRate}/s</div>
        </div>
        <div>
          <div style={{ color: '#4A5A4C', fontSize: '10px', letterSpacing: '1px' }}>TOP TYPE</div>
          <div style={{ color: '#E8F5E9', fontSize: '22px', fontWeight: 700 }}>{stats.topEvent}</div>
        </div>
      </div>
    </div>
  );
}

function CanvasWaveform({ active }) {
  const canvasRef = useRef(null);
  
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    let t = 0;
    let reqId;
    
    function draw() {
      ctx.clearRect(0, 0, 1200, 140);
      
      // Draw Grid
      ctx.strokeStyle = '#1E2A1C';
      ctx.lineWidth = 1;
      for (let x = 0; x < 1200; x += 40) {
        ctx.beginPath();
        ctx.moveTo(x, 0);
        ctx.lineTo(x, 140);
        ctx.stroke();
      }
      
      // Draw Waveform
      ctx.beginPath();
      ctx.strokeStyle = '#5EEAA0';
      ctx.lineWidth = 2;
      for (let x = 0; x < 1200; x++) {
        // Only trigger 'spikes' rapidly if active
        const multiplier = active ? 30 : 5;
        const spike = (Math.sin((x + t) * 0.02) * 10) + (Math.sin((x + t) * 0.15) > 0.97 ? multiplier * Math.random() : 0);
        const y = 70 - spike - (Math.sin((x + t) * 0.008) * 15);
        if (x === 0) ctx.moveTo(x, y); 
        else ctx.lineTo(x, y);
      }
      ctx.stroke();
      
      t += 4;
      reqId = requestAnimationFrame(draw);
    }
    
    draw();
    
    return () => cancelAnimationFrame(reqId);
  }, [active]);

  return (
    <canvas 
      ref={canvasRef} 
      width="1200" 
      height="140" 
      style={{ width: '100%', height: '140px', display: 'block' }}
    />
  );
}
