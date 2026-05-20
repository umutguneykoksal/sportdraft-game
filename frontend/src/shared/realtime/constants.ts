/** Failover threshold: switch from WebSocket to SSE after this many consecutive failures. */
export const WS_FAILOVER_ATTEMPTS = 3;

/** In-memory cap for live WS/SSE envelopes; drained by sync hooks, not a full session log. */
export const MAX_BUFFERED_EVENTS = 2_000;

/** Dedup set cap for processed event IDs across long sessions. */
export const MAX_SEEN_EVENT_IDS = 5_000;
