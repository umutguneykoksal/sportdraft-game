import { useEffect, useRef, useState } from "react";
import { getApiBase } from "../api";
import { createTransport } from "./transportFactory";
import { RealtimeEnvelope } from "./types";

const WS_FAILOVER_ATTEMPTS = 3;
const MAX_BUFFERED_EVENTS = 120;

export function useRealtimeSession(
  sessionId: string | null,
  spectator = false,
  reconnectKey = 0,
  onActivity?: () => void
) {
  const [events, setEvents] = useState<RealtimeEnvelope[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const reconnectTimerRef = useRef<number | null>(null);
  const attemptsRef = useRef(0);
  const onActivityRef = useRef(onActivity);
  const transportModeRef = useRef<"websocket" | "sse">(
    (process.env.REACT_APP_REALTIME_TRANSPORT ?? "websocket").toLowerCase() === "sse"
      ? "sse"
      : "websocket"
  );

  useEffect(() => {
    onActivityRef.current = onActivity;
  }, [onActivity]);

  useEffect(() => {
    if (!sessionId) {
      return;
    }
    const token = localStorage.getItem("sessionToken");
    if (!token) {
      return;
    }

    const envPreferred =
      (process.env.REACT_APP_REALTIME_TRANSPORT ?? "websocket").toLowerCase() === "sse"
        ? "sse"
        : "websocket";
    transportModeRef.current = envPreferred;

    const base = getApiBase().replace(/\/$/, "");
    const wsBase = base.startsWith("https://")
      ? base.replace(/^https:\/\//, "wss://")
      : base.replace(/^http:\/\//, "ws://");
    const spectatorParam = spectator ? "&spectator=true" : "";
    const wsUrl = `${wsBase}/ws/sessions?sessionId=${encodeURIComponent(sessionId)}&token=${encodeURIComponent(token)}${spectatorParam}`;
    const sseUrl = `${base}/api/sessions/${encodeURIComponent(sessionId)}/events?token=${encodeURIComponent(token)}${spectatorParam}`;

    let closed = false;

    const noteActivity = () => {
      onActivityRef.current?.();
    };

    const connect = () => {
      if (reconnectTimerRef.current !== null) {
        window.clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }

      const preferred = transportModeRef.current;

      const transport = createTransport(preferred, wsUrl, sseUrl, {
        onOpen: () => {
          attemptsRef.current = 0;
          noteActivity();
          setIsConnected(true);
        },
        onClose: () => {
          setIsConnected(false);
          if (!closed) {
            attemptsRef.current += 1;
            if (
              envPreferred === "websocket" &&
              transportModeRef.current === "websocket" &&
              attemptsRef.current >= WS_FAILOVER_ATTEMPTS
            ) {
              transportModeRef.current = "sse";
              attemptsRef.current = 0;
              reconnectTimerRef.current = window.setTimeout(connect, 500);
              return;
            }
            const delayMs = Math.min(5000, 1000 + attemptsRef.current * 500);
            reconnectTimerRef.current = window.setTimeout(connect, delayMs);
          }
        },
        onError: () => {
          setIsConnected(false);
        },
        onMessage: (data) => {
          noteActivity();
          try {
            const parsed = JSON.parse(data) as RealtimeEnvelope;
            if (parsed.eventType === "pong") {
              return;
            }
            setEvents((prev) => {
              const next = [...prev, parsed];
              return next.length > MAX_BUFFERED_EVENTS ? next.slice(-MAX_BUFFERED_EVENTS) : next;
            });
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
  }, [sessionId, spectator, reconnectKey]);

  return { events, isConnected };
}
