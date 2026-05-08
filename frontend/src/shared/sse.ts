import { useRealtimeSession } from "./realtime/useRealtimeSession";
import { RealtimeEnvelope } from "./realtime/types";

export type SseEventEnvelope = RealtimeEnvelope;

/**
 * Server-Sent Events (EventSource): one-way server push over HTTP.
 * Mutations stay on REST; this is only for live notifications.
 * Token is passed as a query param because EventSource cannot set headers.
 */
export function useSessionStream(sessionId: string | null) {
  return useRealtimeSession(sessionId);
}
