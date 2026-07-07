import React, { useState, useEffect, useRef } from 'react';
import LoginPage from './components/LoginPage.jsx';
// ConnectionStatus removed — header now uses inline mockup styles
import HeroStrip from './components/HeroStrip.jsx';
import RecentEvents from './components/RecentEvents.jsx';
import DailyTrendChart from './components/DailyTrendChart.jsx';
import TopEventsChart from './components/TopEventsChart.jsx';
import EventsLog from './components/EventsLog.jsx';
import {
  getToken,
  clearToken,
  getProfile,
  getRealtimeSnapshot,
  getSummary,
  getDailyStats,
  getTopEvents,
} from './services/api.js';
import { connect, disconnect } from './services/websocket.js';

export default function App() {
  const [isLoggedIn, setIsLoggedIn] = useState(!!getToken());
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('dashboard');

  // Connection state from WebSocket
  const [connState, setConnState] = useState('disconnected');

  // Real-time & Summary Stats
  const [stats, setStats] = useState({
    todayEvents: 0,
    todayActiveUsers: 0,
    queueSize: 0,
    processingRate: 0, // events/sec
    topEvent: '—',
  });

  // Events list (newest first, capped at 50)
  const [events, setEvents] = useState([]);

  // Historical Chart data
  const [dailyTrend, setDailyTrend] = useState([]);
  const [topEventsData, setTopEventsData] = useState([]);

  const pollingTimer = useRef(null);
  const historyTimer = useRef(null);

  // Check login on load
  useEffect(() => {
    if (isLoggedIn) {
      loadUserProfile();
    } else {
      setLoading(false);
    }
  }, [isLoggedIn]);

  async function loadUserProfile() {
    try {
      const profile = await getProfile();
      setUser(profile);
      // Now initialize dashboard data
      await initDashboard();
    } catch (err) {
      console.error('Failed to load user profile, logging out', err);
      handleLogout();
    } finally {
      setLoading(false);
    }
  }

  function handleLoginSuccess() {
    setIsLoggedIn(true);
    setLoading(true);
  }

  function handleLogout() {
    clearToken();
    disconnect();
    setIsLoggedIn(false);
    setUser(null);
    if (pollingTimer.current) clearInterval(pollingTimer.current);
    if (historyTimer.current) clearInterval(historyTimer.current);
  }

  // Fetch initial snapshot and fetch historical chart data
  async function initDashboard() {
    try {
      // 1. Fetch initial snapshot
      const snapshot = await getRealtimeSnapshot();
      updateFromSnapshot(snapshot);

      // 2. Fetch history
      await refreshHistory();

      // 3. Connect WebSocket
      connect({
        onEvent: handleIncomingEvent,
        onStats: handleIncomingStats,
        onConnectionChange: handleConnectionChange,
      });

      // 4. Start historical refresh timer (every 60 seconds)
      if (historyTimer.current) clearInterval(historyTimer.current);
      historyTimer.current = setInterval(refreshHistory, 60000);
    } catch (err) {
      console.error('Error initializing dashboard', err);
    }
  }

  // Update stats & event stream from realtime snapshot
  function updateFromSnapshot(snapshot) {
    const summary = snapshot.summary || {};
    setStats({
      todayEvents: summary.todayEvents || 0,
      todayActiveUsers: summary.todayActiveUsers || 0,
      queueSize: snapshot.queueSize || 0,
      processingRate: snapshot.processingRatePerMinute
        ? parseFloat((snapshot.processingRatePerMinute / 60).toFixed(2))
        : 0,
      topEvent: summary.topSearch || summary.topCountry || '—', // Use what's available
    });

    const parsedEvents = (snapshot.recentEvents || []).map((e) => ({
      eventId: e.id || e.eventId,
      eventType: e.eventType,
      userId: e.userId,
      source: e.source,
      processedAt: e.processedAt || e.receivedAt,
    }));
    setEvents(parsedEvents.slice(0, 50));
  }

  // Fetch historical chart datasets
  async function refreshHistory() {
    try {
      const [daily, top] = await Promise.all([
        getDailyStats(7), // last 7 days
        getTopEvents(5),  // top 5 event types
      ]);
      setDailyTrend(daily);
      setTopEventsData(top);
    } catch (err) {
      console.error('Error loading chart data', err);
    }
  }

  // Handlers for real-time WebSocket frames
  function handleIncomingEvent(eventPayload) {
    // Add new event to the top of list, cap at 50, mark as new for animation
    setEvents((prev) => {
      const newEvent = { ...eventPayload, _new: true };
      const updated = [newEvent, ...prev.filter((e) => e.eventId !== eventPayload.eventId)];
      return updated.slice(0, 50);
    });
  }

  function handleIncomingStats(statsPayload) {
    // WebSocket StatsBroadcast: { totalEventsToday, activeUsersToday, timestamp }
    setStats((prev) => ({
      ...prev,
      todayEvents: statsPayload.totalEventsToday,
      todayActiveUsers: statsPayload.activeUsersToday,
      // Since WebSocket is alive, queue size is likely zero or near zero, or we maintain previous REST/polling value
    }));
  }

  function handleConnectionChange(state) {
    setConnState(state);
  }

  // Trigger fallback polling if disconnected
  useEffect(() => {
    if (!isLoggedIn) return;

    if (connState !== 'connected') {
      // Set up 5s polling fallback for /analytics/summary
      if (!pollingTimer.current) {
        console.warn('WebSocket disconnected. Starting fallback polling every 5s.');
        pollingTimer.current = setInterval(async () => {
          try {
            const summary = await getSummary();
            // In polling fallback, also get queue size/rate from endpoint if available or default
            setStats((prev) => ({
              ...prev,
              todayEvents: summary.todayEvents || 0,
              todayActiveUsers: summary.todayActiveUsers || 0,
              topEvent: summary.topSearch || summary.topCountry || prev.topEvent,
            }));
          } catch (err) {
            console.error('Fallback polling failed', err);
          }
        }, 5000);
      }
    } else {
      // Clean up fallback polling once WebSocket is connected
      if (pollingTimer.current) {
        console.info('WebSocket connected. Stopping fallback polling.');
        clearInterval(pollingTimer.current);
        pollingTimer.current = null;
      }
    }

    return () => {
      if (pollingTimer.current) {
        clearInterval(pollingTimer.current);
        pollingTimer.current = null;
      }
    };
  }, [connState, isLoggedIn]);

  // Clean up on unmount
  useEffect(() => {
    return () => {
      disconnect();
      if (pollingTimer.current) clearInterval(pollingTimer.current);
      if (historyTimer.current) clearInterval(historyTimer.current);
    };
  }, []);

  if (!isLoggedIn) {
    return <LoginPage onLoginSuccess={handleLoginSuccess} />;
  }

  if (loading) {
    return (
      <div className="loading-container">
        <div className="loading-spinner"></div>
        <div className="loading-text">Loading dashboard…</div>
      </div>
    );
  }

  // Determine topEvent display: if we have topEventsData, use the top item.
  const displayTopEvent = topEventsData.length > 0 ? topEventsData[0].eventType : stats.topEvent;

  return (
    <div style={{ background: 'var(--bg-secondary)', borderRadius: '12px', padding: '16px', border: '0.5px solid var(--border-subtle)', maxWidth: '1200px', margin: '2rem auto' }}>
      <div style={{ background: '#0A0D0A', borderRadius: '8px', overflow: 'hidden', fontFamily: "'JetBrains Mono', monospace" }}>
        
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', padding: '10px 16px', borderBottom: '1px solid #1E2A1C' }}>
          <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#5EEAA0', boxShadow: '0 0 6px #5EEAA0' }}></div>
          <span style={{ color: '#E8F5E9', fontFamily: "'Space Grotesk', sans-serif", fontWeight: 500, fontSize: '14px', letterSpacing: '0.5px' }}>PULSEFLOW</span>
          
          <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ color: '#7A8B7D', fontSize: '11px' }}>{user ? user.username : 'admin'}</span>
            <button 
              onClick={handleLogout} 
              style={{ background: 'none', border: 'none', color: '#4A5A4C', fontSize: '10px', cursor: 'pointer', textDecoration: 'underline' }}
            >
              Logout
            </button>
          </div>
        </div>

        <main>
          {/* Tab Navigation (Simplified) */}
          <nav style={{ display: 'flex', gap: '16px', padding: '12px 16px', borderBottom: '1px solid #1E2A1C' }}>
            <button
              style={{ background: 'none', border: 'none', color: activeTab === 'dashboard' ? '#5EEAA0' : '#7A8B7D', fontSize: '11px', cursor: 'pointer', letterSpacing: '1px', textTransform: 'uppercase' }}
              onClick={() => setActiveTab('dashboard')}
            >
              Overview
            </button>
            <button
              style={{ background: 'none', border: 'none', color: activeTab === 'events' ? '#5EEAA0' : '#7A8B7D', fontSize: '11px', cursor: 'pointer', letterSpacing: '1px', textTransform: 'uppercase' }}
              onClick={() => setActiveTab('events')}
            >
              Event Log
            </button>
          </nav>

        {activeTab === 'dashboard' ? (
          <>
            {/* Hero Strip */}
            <HeroStrip stats={{...stats, topEvent: displayTopEvent}} latestEventTime={events.length > 0 && events[0]._new ? Date.now() : null} />

            {/* Charts Row */}
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1px', background: '#1E2A1C', marginTop: '16px' }}>
              <DailyTrendChart data={dailyTrend} />
              <TopEventsChart data={topEventsData} />
            </div>

            {/* Recent Events List */}
            <RecentEvents events={events} />
          </>
        ) : (
          <EventsLog />
        )}
      </main>
      </div>
    </div>
  );
}
