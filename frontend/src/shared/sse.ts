import { useEffect, useRef, useState } from "react";
import { getApiBase } from "./api";

export interface SseEventEnvelope {
  eventType: string;
  occurredAt: string;
  payload: unknown;
}

export function useSessionStream(sessionId: string | null) {
  const [events, setEvents] = useState<SseEventEnvelope[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const socketRef = useRef<WebSocket | null>(null);
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

    const wsBase = getApiBase().replace(/^http/, "ws");
    const connect = () => {
      const socket = new WebSocket(`${wsBase}/ws/sessions/${sessionId}?token=${token}`);
      socketRef.current = socket;
      setIsConnected(false);

      socket.onopen = () => {
        setIsConnected(true);
      };

      socket.onmessage = (event) => {
        try {
          const parsed = JSON.parse(event.data) as SseEventEnvelope;
          setEvents((prev) => [...prev.slice(-30), parsed]);
        } catch {
          // ignore parse issues for now
        }
      };

      socket.onclose = () => {
        setIsConnected(false);
        if (!closedByHook) {
          reconnectTimerRef.current = window.setTimeout(connect, 1000);
        }
      };

      socket.onerror = () => {
        setIsConnected(false);
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
      socketRef.current?.close();
      socketRef.current = null;
    };
  }, [sessionId]);

  return { events, isConnected };
}
