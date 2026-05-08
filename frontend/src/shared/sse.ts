import { useEffect, useRef, useState } from "react";
import { getApiBase } from "./api";

export interface SseEventEnvelope {
  eventType: string;
  occurredAt: string;
  payload: unknown;
}

const SSE_EVENT = "game";

/**
 * Server-Sent Events (EventSource): one-way server push over HTTP.
 * Mutations stay on REST; this is only for live notifications.
 * Token is passed as a query param because EventSource cannot set headers.
 */
export function useSessionStream(sessionId: string | null) {
  const [events, setEvents] = useState<SseEventEnvelope[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const sourceRef = useRef<EventSource | null>(null);
  const reconnectTimerRef = useRef<number | null>(null);

  useEffect(() => {
    if (!sessionId) {
      return;
    }
    const token = localStorage.getItem("sessionToken");
    if (!token) {
      return;
    }
    let closedByHook = false;

    const connect = () => {
      if (reconnectTimerRef.current !== null) {
        window.clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      sourceRef.current?.close();
      const base = getApiBase().replace(/\/$/, "");
      const url = `${base}/api/sessions/${encodeURIComponent(sessionId)}/events?token=${encodeURIComponent(token)}`;
      const es = new EventSource(url);
      sourceRef.current = es;
      setIsConnected(false);

      es.addEventListener("open", () => {
        setIsConnected(true);
      });

      es.addEventListener(SSE_EVENT, (evt: MessageEvent) => {
        try {
          const parsed = JSON.parse(String(evt.data)) as SseEventEnvelope;
          setEvents((prev) => [...prev.slice(-30), parsed]);
        } catch {
          // ignore malformed chunks
        }
      });

      es.onerror = () => {
        setIsConnected(false);
        es.close();
        if (!closedByHook) {
          reconnectTimerRef.current = window.setTimeout(connect, 1500);
        }
      };
    };

    connect();

    return () => {
      closedByHook = true;
      setIsConnected(false);
      if (reconnectTimerRef.current !== null) {
        window.clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      sourceRef.current?.close();
      sourceRef.current = null;
    };
  }, [sessionId]);

  return { events, isConnected };
}
