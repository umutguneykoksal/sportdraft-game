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
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimerRef = useRef<number | null>(null);
  const reconnectAttemptsRef = useRef<number>(0);

  useEffect(() => {
    if (!sessionId) {
      return;
    }
    const token = localStorage.getItem("sessionToken");
    if (!token) {
      return;
    }
    let closedByHook = false;

    const wsEnabled = (process.env.REACT_APP_REALTIME_TRANSPORT ?? "sse").toLowerCase() === "websocket";
    const connectSse = () => {
      if (reconnectTimerRef.current !== null) {
        window.clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      wsRef.current?.close();
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
          reconnectAttemptsRef.current += 1;
          const delayMs = Math.min(5000, 1000 + reconnectAttemptsRef.current * 500);
          reconnectTimerRef.current = window.setTimeout(connectSse, delayMs);
        }
      };
    };

    const connectWebSocket = () => {
      if (reconnectTimerRef.current !== null) {
        window.clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      sourceRef.current?.close();
      wsRef.current?.close();
      const httpBase = getApiBase().replace(/\/$/, "");
      const wsBase = httpBase.startsWith("https://")
        ? httpBase.replace(/^https:\/\//, "wss://")
        : httpBase.replace(/^http:\/\//, "ws://");
      const wsUrl =
        `${wsBase}/ws/sessions?sessionId=${encodeURIComponent(sessionId)}` +
        `&token=${encodeURIComponent(token)}`;
      const ws = new WebSocket(wsUrl);
      wsRef.current = ws;
      setIsConnected(false);

      ws.onopen = () => {
        reconnectAttemptsRef.current = 0;
        setIsConnected(true);
      };
      ws.onmessage = (evt) => {
        try {
          const parsed = JSON.parse(String(evt.data)) as SseEventEnvelope;
          setEvents((prev) => [...prev.slice(-30), parsed]);
        } catch {
          // ignore malformed chunks
        }
      };
      ws.onclose = () => {
        setIsConnected(false);
        if (!closedByHook) {
          reconnectAttemptsRef.current += 1;
          const delayMs = Math.min(5000, 1000 + reconnectAttemptsRef.current * 500);
          reconnectTimerRef.current = window.setTimeout(connectWebSocket, delayMs);
        }
      };
      ws.onerror = () => {
        // Fall back to SSE if WS handshake fails repeatedly.
        if (!closedByHook && reconnectAttemptsRef.current >= 2) {
          ws.close();
          connectSse();
        }
      };
    };

    if (wsEnabled) {
      connectWebSocket();
    } else {
      connectSse();
    }

    return () => {
      closedByHook = true;
      setIsConnected(false);
      if (reconnectTimerRef.current !== null) {
        window.clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      sourceRef.current?.close();
      wsRef.current?.close();
      wsRef.current = null;
      sourceRef.current = null;
    };
  }, [sessionId]);

  return { events, isConnected };
}
