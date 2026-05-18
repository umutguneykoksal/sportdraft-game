import { useCallback, useEffect, useRef, useState } from "react";
import { AuctionRoundResponse, AuctionStateResponse, SessionSnapshotResponse } from "../api/contracts";
import { getAuctionState, getSessionSnapshot } from "../api";
import { fetchEventsSince } from "../api/sessionClient";
import { ApiError } from "../api/errors";
import { RealtimeEnvelope } from "./types";
import { useRealtimeSession } from "./useRealtimeSession";

export interface SessionSyncState {
  snapshot: SessionSnapshotResponse | null;
  auctionState: AuctionStateResponse | null;
  round: AuctionRoundResponse | null;
  setRound: (r: AuctionRoundResponse | null) => void;
  isConnected: boolean;
  lastEvent: RealtimeEnvelope | null;
  error: string | null;
  sessionNotFound: boolean;
  matchCompleted: boolean;
  /** @deprecated Always false — kept for interface compat while callers migrate. */
  isRefreshing: boolean;
}

/**
 * Event-driven sync strategy:
 *
 *  bid_placed / turn_passed  → patch round in memory (0 REST calls)
 *  round_ended / round_started → refreshState (getAuctionState only)
 *  match_start_pending / session_finalizing → refreshFull (snapshot + state)
 *
 *  All events in a batch are processed (no early break) so simultaneous
 *  events like bid_placed + round_ended are both handled correctly.
 *
 *  Gap detection: if SSE seq jumps (e.g. after reconnect), we call the
 *  /events/sync ring-buffer endpoint to replay missed events. If the buffer
 *  is exhausted we fall back to refreshFull.
 *
 *  Poll: 30s when SSE is live (safety net), 2s when disconnected (fast recovery).
 *
 *  Visibility: on tab-focus, if >10s since last event → refreshState; if SSE
 *              disconnected → force reconnect.
 *  Heartbeat: if no heartbeat for 45s → force SSE reconnect (silent proxy drop).
 */

/** Safety-net poll when SSE is live and no round is active (budget/formation changes are rare). */
const POLL_INTERVAL_IDLE_MS         = 30_000;
/**
 * Safety-net poll while a round is OPEN and SSE is live.
 * Catches single-event drops (e.g. proxy silently discarded one bid_placed frame)
 * so the stale client recovers within ~5s instead of 30s.
 */
const POLL_INTERVAL_ROUND_OPEN_MS   =  5_000;
const POLL_INTERVAL_DISCONNECTED_MS =  2_000;
const VISIBILITY_REFRESH_THRESHOLD_MS = 10_000;
const HEARTBEAT_TIMEOUT_MS = 45_000;

/** Fields from the SSE round payload that can be safely merged into local round state. */
const SHARED_ROUND_FIELDS: ReadonlyArray<keyof AuctionRoundResponse> = [
  "highestBidCents",
  "highestBidderUserId",
  "currentTurnUserId",
  "endsAt",
  "turnOpenAt",
  "passedPlayerIds",
  "status",
  "opponentCanBid",
];

function pickSharedFields(src: AuctionRoundResponse): Partial<AuctionRoundResponse> {
  const srcAny = src as unknown as Record<string, unknown>;
  const out: Record<string, unknown> = {};
  for (const k of SHARED_ROUND_FIELDS) {
    if (k in src) out[k] = srcAny[k];
  }
  return out as Partial<AuctionRoundResponse>;
}

export function useSessionSync(sessionId: string | null, spectator = false): SessionSyncState {
  const [snapshot, setSnapshot] = useState<SessionSnapshotResponse | null>(null);
  const [auctionState, setAuctionState] = useState<AuctionStateResponse | null>(null);
  const [round, setRound] = useState<AuctionRoundResponse | null>(null);
  const [lastEvent, setLastEvent] = useState<RealtimeEnvelope | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sessionNotFound, setSessionNotFound] = useState(false);
  const [matchCompleted, setMatchCompleted] = useState(false);
  const [reconnectKey, setReconnectKey] = useState(0);

  const { events, isConnected } = useRealtimeSession(sessionId, spectator, reconnectKey);

  const fetchingRef = useRef(false);
  const pendingRefreshRef = useRef(false);
  const lastEventIdxRef = useRef(-1);

  // Seq tracking: last processed server sequence number (0 = never received one)
  const lastServerSeqRef = useRef(0);

  // Timestamps for visibility + heartbeat detection
  const lastEventTimeRef = useRef(Date.now());
  const lastHeartbeatRef = useRef(Date.now());

  const forceReconnect = useCallback(() => {
    setReconnectKey((k) => k + 1);
  }, []);

  // Reset on session change
  useEffect(() => {
    setSnapshot(null);
    setAuctionState(null);
    setRound(null);
    setLastEvent(null);
    setError(null);
    setSessionNotFound(false);
    setMatchCompleted(false);
    fetchingRef.current = false;
    pendingRefreshRef.current = false;
    lastEventIdxRef.current = -1;
    lastServerSeqRef.current = 0;
    lastEventTimeRef.current = Date.now();
    lastHeartbeatRef.current = Date.now();
  }, [sessionId]);

  // ─── Fetch helpers ────────────────────────────────────────────────────────

  const refreshState = useCallback(async (sid: string) => {
    if (fetchingRef.current) {
      pendingRefreshRef.current = true;
      return;
    }
    fetchingRef.current = true;
    pendingRefreshRef.current = false;
    try {
      const state = await getAuctionState(sid);
      setAuctionState(state);
      setRound(state.currentRound);
      setError(null);
      setSessionNotFound(false);
    } catch (e) {
      if (e instanceof ApiError && (e.status === 404 || e.status === 401)) {
        setSessionNotFound(true);
        setError(null);
      } else {
        setError(e instanceof Error ? e.message : "Sync failed");
      }
    } finally {
      fetchingRef.current = false;
      if (pendingRefreshRef.current) {
        pendingRefreshRef.current = false;
        refreshState(sid);
      }
    }
  }, []);

  const refreshFull = useCallback(async (sid: string) => {
    if (fetchingRef.current) {
      pendingRefreshRef.current = true;
      return;
    }
    fetchingRef.current = true;
    pendingRefreshRef.current = false;
    try {
      const [snap, state] = await Promise.all([getSessionSnapshot(sid), getAuctionState(sid)]);
      setSnapshot(snap);
      setAuctionState(state);
      setRound(state.currentRound);
      setError(null);
      setSessionNotFound(false);
    } catch (e) {
      if (e instanceof ApiError && (e.status === 404 || e.status === 401)) {
        setSessionNotFound(true);
        setError(null);
      } else {
        setError(e instanceof Error ? e.message : "Sync failed");
      }
    } finally {
      fetchingRef.current = false;
      if (pendingRefreshRef.current) {
        pendingRefreshRef.current = false;
        refreshFull(sid);
      }
    }
  }, []);

  // ─── Initial load ─────────────────────────────────────────────────────────

  useEffect(() => {
    if (!sessionId) return;
    refreshFull(sessionId);
  }, [sessionId, refreshFull]);

  // ─── Background poll (safety net / disconnected recovery) ─────────────────
  // Interval shrinks to 5s while a round is OPEN so a silently dropped bid_placed
  // event is caught quickly (worst-case 5s lag instead of 30s).

  const roundIsOpen = round?.status === "OPEN";

  useEffect(() => {
    if (!sessionId || sessionNotFound || matchCompleted) return;
    const interval = !isConnected
      ? POLL_INTERVAL_DISCONNECTED_MS
      : roundIsOpen
        ? POLL_INTERVAL_ROUND_OPEN_MS
        : POLL_INTERVAL_IDLE_MS;
    const id = window.setInterval(() => refreshState(sessionId), interval);
    return () => window.clearInterval(id);
  }, [sessionId, isConnected, roundIsOpen, sessionNotFound, matchCompleted, refreshState]);

  // ─── Reconnect → replay missed events from ring buffer ───────────────────

  const prevConnectedRef = useRef(false);
  useEffect(() => {
    const was = prevConnectedRef.current;
    prevConnectedRef.current = isConnected;
    if (!sessionId || sessionNotFound || matchCompleted) return;

    if (isConnected && !was) {
      const lastSeq = lastServerSeqRef.current;
      if (lastSeq === 0) {
        // First connect — initial load handles this
        return;
      }
      // Try to replay missed events from the ring buffer
      fetchEventsSince(sessionId, lastSeq)
        .then(({ events: missed, lastSeq: serverLastSeq }) => {
          if (missed.length === 0) {
            // Buffer exhausted or no missed events — full refresh to be safe
            refreshFull(sessionId);
            return;
          }
          // Replay missed events using the same dispatch logic
          dispatchEvents(missed as RealtimeEnvelope[], sessionId);
          lastServerSeqRef.current = serverLastSeq;
        })
        .catch(() => {
          // Sync endpoint failed → full refresh fallback
          refreshFull(sessionId);
        });
    }
  }, [isConnected, sessionId, sessionNotFound, matchCompleted, refreshFull]);

  // ─── Event dispatcher (shared between live SSE and sync replay) ───────────

  const dispatchEvents = useCallback(
    (envs: RealtimeEnvelope[], sid: string) => {
      let needsStateRefresh = false;
      let needsFullRefresh = false;

      for (const env of envs) {
        if (env.eventType === "heartbeat" || (env.seq ?? 0) === 0) {
          lastHeartbeatRef.current = Date.now();
          continue;
        }

        lastEventTimeRef.current = Date.now();
        lastHeartbeatRef.current = Date.now();
        setLastEvent(env);

        const seq = env.seq ?? 0;

        if (env.eventType === "match_completed") {
          setMatchCompleted(true);
          return; // stop processing; navigation will happen
        }

        switch (env.eventType) {
          case "bid_placed":
          case "turn_passed": {
            // If seq jumped, we may have missed events → force a state refresh after patching
            if (seq > 0 && lastServerSeqRef.current > 0 && seq > lastServerSeqRef.current + 1) {
              needsStateRefresh = true;
            }
            const payload = env.payload as { round?: AuctionRoundResponse };
            if (payload?.round) {
              const incoming = payload.round;
              setRound((prev) => {
                // Only patch if the event belongs to the current round — stale events are ignored
                if (prev && prev.roundId !== incoming.roundId) return prev;
                return prev
                  ? { ...prev, ...pickSharedFields(incoming) }
                  : incoming;
              });
            }
            break;
          }

          case "round_ended":
          case "round_started":
            needsStateRefresh = true;
            break;

          case "match_start_pending":
          case "session_finalizing":
            needsFullRefresh = true;
            break;

          default:
            needsStateRefresh = true;
            break;
        }

        if (seq > 0) {
          lastServerSeqRef.current = Math.max(lastServerSeqRef.current, seq);
        }
      }

      // Batch the REST calls: one full refresh beats one state refresh
      if (needsFullRefresh) {
        refreshFull(sid);
      } else if (needsStateRefresh) {
        refreshState(sid);
      }
    },
    [refreshFull, refreshState]
  );

  // ─── Live SSE event handler ───────────────────────────────────────────────

  useEffect(() => {
    if (!sessionId || events.length === 0 || sessionNotFound || matchCompleted) return;

    const newEvents: RealtimeEnvelope[] = [];
    for (let i = lastEventIdxRef.current + 1; i < events.length; i++) {
      newEvents.push(events[i]);
    }
    lastEventIdxRef.current = events.length - 1;

    if (newEvents.length === 0) return;
    dispatchEvents(newEvents, sessionId);
  }, [events, sessionId, sessionNotFound, matchCompleted, dispatchEvents]);

  // ─── Visibility change detection ─────────────────────────────────────────

  useEffect(() => {
    if (!sessionId || sessionNotFound || matchCompleted) return;

    const handleVisibility = () => {
      if (document.visibilityState !== "visible") return;
      const gap = Date.now() - lastEventTimeRef.current;
      if (gap > VISIBILITY_REFRESH_THRESHOLD_MS) {
        refreshState(sessionId);
      }
      if (!isConnected) {
        forceReconnect();
      }
    };

    document.addEventListener("visibilitychange", handleVisibility);
    return () => document.removeEventListener("visibilitychange", handleVisibility);
  }, [sessionId, isConnected, sessionNotFound, matchCompleted, refreshState, forceReconnect]);

  // ─── Heartbeat timeout → force reconnect on silent disconnect ─────────────

  useEffect(() => {
    if (!sessionId || sessionNotFound || matchCompleted || !isConnected) return;

    const id = window.setInterval(() => {
      if (Date.now() - lastHeartbeatRef.current > HEARTBEAT_TIMEOUT_MS) {
        forceReconnect();
      }
    }, 10_000);

    return () => window.clearInterval(id);
  }, [sessionId, isConnected, sessionNotFound, matchCompleted, forceReconnect]);

  return {
    snapshot,
    auctionState,
    round,
    setRound,
    isConnected,
    lastEvent,
    error,
    sessionNotFound,
    matchCompleted,
    isRefreshing: false,
  };
}
