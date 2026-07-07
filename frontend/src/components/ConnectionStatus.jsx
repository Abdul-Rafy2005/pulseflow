import React from 'react';

export default function ConnectionStatus({ state }) {
  const labels = {
    connected: 'Live',
    reconnecting: 'Reconnecting…',
    disconnected: 'Disconnected',
  };

  return (
    <div className={`connection-status connection-status--${state}`} id="connection-status">
      <span className="connection-status__dot" />
      <span>{labels[state] || 'Unknown'}</span>
    </div>
  );
}
