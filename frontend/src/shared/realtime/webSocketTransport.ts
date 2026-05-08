import { RealtimeTransport } from "./types";

interface WsCallbacks {
  onOpen: () => void;
  onClose: () => void;
  onError: () => void;
  onMessage: (data: string) => void;
}

export function createWebSocketTransport(url: string, callbacks: WsCallbacks): RealtimeTransport {
  let socket: WebSocket | null = null;
  return {
    connect() {
      socket?.close();
      socket = new WebSocket(url);
      socket.onopen = callbacks.onOpen;
      socket.onclose = callbacks.onClose;
      socket.onerror = callbacks.onError;
      socket.onmessage = (evt) => callbacks.onMessage(String(evt.data));
    },
    close() {
      socket?.close();
      socket = null;
    },
  };
}
