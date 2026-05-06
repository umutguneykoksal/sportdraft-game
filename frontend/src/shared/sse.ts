import { useEffect, useRef, useState } from "react";
import { getApiBase } from "./api";

export interface SseEventEnvelope {
  eventType: string;
  occurredAt: string;
  payload: unknown;
}

export function useSessionStream(sessionId: string | null) {
  const [events, setEvents] = useState<SseEventEnvelope[]>([]);
  const sourceRef = useRef<EventSource | null>(null);

  useEffect(() => {
    if (!sessionId) {
      return;
    }
    const token = localStorage.getItem("sessionToken");
    if (!token) {
      return;
    }

    const source = new EventSource(
      `${getApiBase()}/api/sessions/${sessionId}/events?token=${token}`
    );
    sourceRef.current = source;

    const push = (event: MessageEvent<string>) => {
      try {
        const parsed = JSON.parse(event.data) as SseEventEnvelope;
        setEvents((prev) => [...prev.slice(-30), parsed]);
      } catch {
        // ignore parse issues for now
      }
    };

    const names = [
      "lobby_updated",
      "countdown_started",
      "countdown_cancelled",
      "formation_phase_started",
      "formation_chosen",
      "auctioning_started",
      "round_started",
      "bid_placed",
      "round_ended",
      "round_skipped",
      "session_finalizing",
      "match_completed",
      "heartbeat",
    ];

    names.forEach((name) => source.addEventListener(name, push as EventListener));

    return () => {
      names.forEach((name) => source.removeEventListener(name, push as EventListener));
      source.close();
      sourceRef.current = null;
    };
  }, [sessionId]);

  return { events };
}
