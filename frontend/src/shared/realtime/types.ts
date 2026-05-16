export interface RealtimeEnvelope {
  eventId?: string;
  sessionId?: string;
  eventType: string;
  /** Monoton sequence number per session. 0 means unsequenced (heartbeat). Optional for backward compat. */
  seq?: number;
  occurredAt: string;
  payload: unknown;
}

export interface RealtimeTransport {
  connect(): void;
  close(): void;
}
