import { useEffect, useRef, useState } from "react";
import { getApiBase } from "../api";
import { createTransport } from "./transportFactory";
import { RealtimeEnvelope } from "./types";

export function useRealtimeSession(sessionId: string | null, spectator = false, reconnectKey = 0) {
  const [events, setEvents] = useState<RealtimeEnvelope[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const reconnectTimerRef = useRef<number | null>(null);
  const attemptsRef = useRef(0);

  useEffect(() => {
    if (!sessionId) {
      return;
    }
    const token = localStorage.getItem("sessionToken");
    if (!token) {
      return;
    }

    const base = getApiBase().replace(/\/$/, "");
    const wsBase = base.startsWith("https://")
      ? base.replace(/^https:\/\//, "wss://")
      : base.replace(/^http:\/\//, "ws://");
    const spectatorParam = spectator ? "&spectator=true" : "";
    const wsUrl = `${wsBase}/ws/sessions?sessionId=${encodeURIComponent(sessionId)}&token=${encodeURIComponent(token)}${spectatorParam}`;
    const sseUrl = `${base}/api/sessions/${encodeURIComponent(sessionId)}/events?token=${encodeURIComponent(token)}${spectatorParam}`;

    let closed = false;
    // Default to WebSocket — more reliable on reverse-proxy infra (Railway) than SSE.
    // Set REACT_APP_REALTIME_TRANSPORT=sse to force SSE (e.g. for debugging).
    const preferred =
      (process.env.REACT_APP_REALTIME_TRANSPORT ?? "websocket").toLowerCase() === "sse" ? "sse" : "websocket";

    const connect = () => {
      if (reconnectTimerRef.current !== null) {
        window.clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      const transport = createTransport(preferred, wsUrl, sseUrl, {
        onOpen: () => {
          attemptsRef.current = 0;
          setIsConnected(true);
        },
        onClose: () => {
          setIsConnected(false);
          if (!closed) {
            attemptsRef.current += 1;
            const delayMs = Math.min(5000, 1000 + attemptsRef.current * 500);
            reconnectTimerRef.current = window.setTimeout(connect, delayMs);
          }
        },
        onError: () => {
          setIsConnected(false);
        },
        onMessage: (data) => {
          try {
            const parsed = JSON.parse(data) as RealtimeEnvelope;
            setEvents((prev) => [...prev.slice(-30), parsed]);
          } catch {
            // ignore malformed payload
          }
        },
      });
      transport.connect();

      return () => transport.close();
    };

    const disconnect = connect();

    return () => {
      closed = true;
      setIsConnected(false);
      if (reconnectTimerRef.current !== null) {
        window.clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      disconnect?.();
    };
  }, [sessionId, reconnectKey]);

  return { events, isConnected };
}
