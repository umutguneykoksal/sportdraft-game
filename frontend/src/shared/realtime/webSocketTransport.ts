import { RealtimeTransport } from "./types";

interface WsCallbacks {
  onOpen: () => void;
  onClose: () => void;
  onError: () => void;
  onMessage: (data: string) => void;
}

/** Client→server ping keeps Railway/proxy idle timers from dropping the socket. */
const CLIENT_PING_INTERVAL_MS = 25_000;

export function createWebSocketTransport(url: string, callbacks: WsCallbacks): RealtimeTransport {
  let socket: WebSocket | null = null;
  let pingTimer: ReturnType<typeof setInterval> | null = null;

  const clearPing = () => {
    if (pingTimer !== null) {
      clearInterval(pingTimer);
      pingTimer = null;
    }
  };

  return {
    connect() {
      clearPing();
      socket?.close();
      socket = new WebSocket(url);
      socket.onopen = () => {
        callbacks.onOpen();
        pingTimer = setInterval(() => {
          if (socket?.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({ type: "ping" }));
          }
        }, CLIENT_PING_INTERVAL_MS);
      };
      socket.onclose = () => {
        clearPing();
        callbacks.onClose();
      };
      socket.onerror = () => {
        callbacks.onError();
      };
      socket.onmessage = (evt) => callbacks.onMessage(String(evt.data));
    },
    close() {
      clearPing();
      socket?.close();
      socket = null;
    },
  };
}
