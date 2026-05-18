import { createSseTransport } from "./sseTransport";
import { createWebSocketTransport } from "./webSocketTransport";
import { RealtimeTransport } from "./types";

interface TransportCallbacks {
  onOpen: () => void;
  onClose: () => void;
  onError: () => void;
  onMessage: (data: string) => void;
}

export function createTransport(
  preferred: "websocket" | "sse",
  wsUrl: string,
  sseUrl: string,
  callbacks: TransportCallbacks
): RealtimeTransport {
  if (preferred === "websocket") {
    return createWebSocketTransport(wsUrl, callbacks);
  }
  return createSseTransport(sseUrl, callbacks);
}
