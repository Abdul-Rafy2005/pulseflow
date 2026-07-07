import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client/dist/sockjs';

/**
 * WebSocket service using STOMP over SockJS.
 *
 * Connects to /ws, subscribes to /topic/events and /topic/stats.
 * Provides automatic reconnection and exposes connection state.
 */

const WS_URL = '/ws';
const RECONNECT_DELAY = 3000;

let client = null;
let onEventCallback = null;
let onStatsCallback = null;
let onConnectionChangeCallback = null;

/**
 * Connection states: 'connected' | 'reconnecting' | 'disconnected'
 */
let connectionState = 'disconnected';

function setConnectionState(state) {
  connectionState = state;
  if (onConnectionChangeCallback) {
    onConnectionChangeCallback(state);
  }
}

export function getConnectionState() {
  return connectionState;
}

/**
 * Connect to the WebSocket and subscribe to topics.
 *
 * @param {Object} callbacks
 * @param {Function} callbacks.onEvent - called with each EventBroadcast payload
 * @param {Function} callbacks.onStats - called with each StatsBroadcast payload
 * @param {Function} callbacks.onConnectionChange - called with connection state string
 */
export function connect({ onEvent, onStats, onConnectionChange }) {
  onEventCallback = onEvent;
  onStatsCallback = onStats;
  onConnectionChangeCallback = onConnectionChange;

  if (client && client.connected) {
    return;
  }

  client = new Client({
    webSocketFactory: () => new SockJS(WS_URL),
    reconnectDelay: RECONNECT_DELAY,
    debug: () => {}, // silence debug logs

    onConnect: () => {
      setConnectionState('connected');

      client.subscribe('/topic/events', (message) => {
        try {
          const event = JSON.parse(message.body);
          if (onEventCallback) onEventCallback(event);
        } catch (e) {
          console.error('Failed to parse event message:', e);
        }
      });

      client.subscribe('/topic/stats', (message) => {
        try {
          const stats = JSON.parse(message.body);
          if (onStatsCallback) onStatsCallback(stats);
        } catch (e) {
          console.error('Failed to parse stats message:', e);
        }
      });
    },

    onStompError: (frame) => {
      console.error('STOMP error:', frame.headers?.message);
      setConnectionState('reconnecting');
    },

    onWebSocketClose: () => {
      setConnectionState('reconnecting');
    },

    onDisconnect: () => {
      setConnectionState('disconnected');
    },
  });

  client.activate();
}

/**
 * Disconnect from the WebSocket.
 */
export function disconnect() {
  if (client) {
    client.deactivate();
    client = null;
    setConnectionState('disconnected');
  }
}
