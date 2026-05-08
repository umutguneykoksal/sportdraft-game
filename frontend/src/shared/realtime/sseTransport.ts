import { RealtimeTransport } from "./types";

interface SseCallbacks {
  onOpen: () => void;
  onError: () => void;
  onMessage: (data: string) => void;
}

export function createSseTransport(url: string, callbacks: SseCallbacks): RealtimeTransport {
  let source: EventSource | null = null;
  return {
    connect() {
      source?.close();
      source = new EventSource(url);
      source.addEventListener("open", callbacks.onOpen);
      source.addEventListener("game", (evt: MessageEvent) => callbacks.onMessage(String(evt.data)));
      source.onerror = callbacks.onError;
    },
    close() {
      source?.close();
      source = null;
    },
  };
}
