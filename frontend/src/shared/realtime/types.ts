export interface RealtimeEnvelope {
  eventId?: string;
  sessionId?: string;
  eventType: string;
  version?: number;
  occurredAt: string;
  payload: unknown;
}

export interface RealtimeTransport {
  connect(): void;
  close(): void;
}
