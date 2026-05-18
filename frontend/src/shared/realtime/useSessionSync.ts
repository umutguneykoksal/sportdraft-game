import { useCallback, useEffect, useRef, useState } from "react";
import { AuctionRoundResponse, AuctionStateResponse, SessionSnapshotResponse } from "../api/contracts";
import { getAuctionState, getSessionSnapshot } from "../api";
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
 *  Poll: 30s when SSE is live (safety net), 2s when disconnected (fast recovery).
 *
 *  Visibility: on tab-focus, if >10s since last event → refreshState; if SSE
 *              disconnected → force reconnect.
 *  Heartbeat: if no heartbeat for 45s → force SSE reconnect (silent proxy drop).
 */

const POLL_INTERVAL_CONNECTED_MS   = 30_000;
const POLL_INTERVAL_DISCONNECTED_MS = 2_000;
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

  // Track timestamps for visibility + heartbeat detection
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

  useEffect(() => {
    if (!sessionId || sessionNotFound || matchCompleted) return;
    const interval = isConnected ? POLL_INTERVAL_CONNECTED_MS : POLL_INTERVAL_DISCONNECTED_MS;
    const id = window.setInterval(() => refreshState(sessionId), interval);
    return () => window.clearInterval(id);
  }, [sessionId, isConnected, sessionNotFound, matchCompleted, refreshState]);

  // ─── Reconnect on reconnect → full refresh ─────────────────────────────────

  const prevConnectedRef = useRef(false);
  useEffect(() => {
    const was = prevConnectedRef.current;
    prevConnectedRef.current = isConnected;
    if (!sessionId || sessionNotFound || matchCompleted) return;
    if (isConnected && !was) {
      refreshFull(sessionId);
    }
  }, [isConnected, sessionId, sessionNotFound, matchCompleted, refreshFull]);

  // ─── SSE event handler ────────────────────────────────────────────────────

  useEffect(() => {
    if (!sessionId || events.length === 0 || sessionNotFound || matchCompleted) return;

    for (let i = lastEventIdxRef.current + 1; i < events.length; i++) {
      const env = events[i];

      if (env.eventType === "heartbeat" || (env.seq ?? 0) === 0) {
        lastHeartbeatRef.current = Date.now();
        continue;
      }

      lastEventTimeRef.current = Date.now();
      lastHeartbeatRef.current = Date.now();

      if (env.eventType === "match_completed") {
        setMatchCompleted(true);
        setLastEvent(env);
        lastEventIdxRef.current = events.length - 1;
        return;
      }

      setLastEvent(env);

      switch (env.eventType) {
        case "bid_placed":
        case "turn_passed": {
          // Patch round in memory from SSE payload — 0 REST calls
          const payload = env.payload as { round?: AuctionRoundResponse };
          if (payload?.round) {
            setRound((prev) =>
              prev ? { ...prev, ...pickSharedFields(payload.round!) } : payload.round!
            );
          }
          break;
        }

        case "round_ended":
        case "round_started":
          // Team/budget may have changed — fetch updated auction state
          refreshState(sessionId);
          break;

        case "match_start_pending":
        case "session_finalizing":
          // Session status changed — need full snapshot + auction state
          refreshFull(sessionId);
          break;

        default:
          // For any other events, do a state refresh
          refreshState(sessionId);
          break;
      }

      break; // one dispatch per batch
    }

    lastEventIdxRef.current = events.length - 1;
  }, [events, sessionId, sessionNotFound, matchCompleted, refreshState, refreshFull]);

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
