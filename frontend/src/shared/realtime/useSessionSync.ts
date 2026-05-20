import { useCallback, useEffect, useRef, useState } from "react";
import { AuctionRoundResponse, AuctionStateResponse, SessionSnapshotResponse } from "../api/contracts";
import { getAuctionState, getSessionSnapshot } from "../api";
import { fetchEventsSince } from "../api/sessionClient";
import { ApiError } from "../api/errors";
import {
  decideReplayAction,
  drainUnseenEvents,
  mergeRoundFromRest,
  pickSharedFields,
  processEventBatch,
  RefreshKind,
  sortBySeq,
} from "./eventDispatch";
import { RealtimeEnvelope } from "./types";
import { useRealtimeSession } from "./useRealtimeSession";
import {
  HEARTBEAT_TIMEOUT_MS,
  POLL_INTERVAL_DISCONNECTED_MS,
  POLL_INTERVAL_IDLE_MS,
  POLL_INTERVAL_ROUND_OPEN_MS,
  STALE_GAME_EVENT_MS,
  VISIBILITY_REFRESH_THRESHOLD_MS,
} from "./syncConstants";

export interface SessionSyncState {
  snapshot: SessionSnapshotResponse | null;
  auctionState: AuctionStateResponse | null;
  round: AuctionRoundResponse | null;
  setRound: (r: AuctionRoundResponse | null) => void;
  /** Apply an HTTP mutation response immediately; bumps the actor version guard. */
  applyActorRound: (r: AuctionRoundResponse | null) => void;
  isConnected: boolean;
  lastEvent: RealtimeEnvelope | null;
  error: string | null;
  sessionNotFound: boolean;
  matchCompleted: boolean;
  isRefreshing: boolean;
}

export function useSessionSync(sessionId: string | null, spectator = false): SessionSyncState {
  const [snapshot, setSnapshot] = useState<SessionSnapshotResponse | null>(null);
  const [auctionState, setAuctionState] = useState<AuctionStateResponse | null>(null);
  const [round, setRound] = useState<AuctionRoundResponse | null>(null);
  const [lastEvent, setLastEvent] = useState<RealtimeEnvelope | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sessionNotFound, setSessionNotFound] = useState(false);
  const [matchCompleted, setMatchCompleted] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [reconnectKey, setReconnectKey] = useState(0);

  const fetchingRef = useRef(false);
  const pendingRefreshRef = useRef<RefreshKind>("none");
  const fetchGenRef = useRef(0);
  const lastServerSeqRef = useRef(0);
  const lastEventTimeRef = useRef(Date.now());
  const lastHeartbeatRef = useRef(Date.now());
  const roundRef = useRef<AuctionRoundResponse | null>(null);
  const lastMutationVersionRef = useRef(0);
  const seenEventIdsRef = useRef(new Set<string>());

  const noteTransportActivity = useCallback(() => {
    lastHeartbeatRef.current = Date.now();
  }, []);

  const forceReconnect = useCallback(() => {
    setReconnectKey((k) => k + 1);
  }, []);

  const { events, isConnected } = useRealtimeSession(
    sessionId,
    spectator,
    reconnectKey,
    noteTransportActivity
  );

  useEffect(() => {
    roundRef.current = round;
  }, [round]);

  useEffect(() => {
    setSnapshot(null);
    setAuctionState(null);
    setRound(null);
    setLastEvent(null);
    setError(null);
    setSessionNotFound(false);
    setMatchCompleted(false);
    setIsRefreshing(false);
    fetchingRef.current = false;
    pendingRefreshRef.current = "none";
    fetchGenRef.current = 0;
    lastServerSeqRef.current = 0;
    lastEventTimeRef.current = Date.now();
    lastHeartbeatRef.current = Date.now();
    lastMutationVersionRef.current = 0;
    seenEventIdsRef.current.clear();
  }, [sessionId]);

  const applyRoundSafe = useCallback((incoming: AuctionRoundResponse | null) => {
    setRound((current) => mergeRoundFromRest(current, incoming));
  }, []);

  const applyActorRound = useCallback((incoming: AuctionRoundResponse | null) => {
    if (incoming?.version != null) {
      lastMutationVersionRef.current = Math.max(lastMutationVersionRef.current, incoming.version);
    }
    setRound(incoming);
  }, []);

  const runRefresh = useCallback(async (sid: string, kind: RefreshKind) => {
    if (kind === "none") return;
    if (fetchingRef.current) {
      pendingRefreshRef.current =
        pendingRefreshRef.current === "full" || kind === "full" ? "full" : kind;
      return;
    }

    fetchingRef.current = true;
    setIsRefreshing(true);
    const gen = ++fetchGenRef.current;

    try {
      if (kind === "full") {
        const [snap, state] = await Promise.all([getSessionSnapshot(sid), getAuctionState(sid)]);
        if (gen !== fetchGenRef.current) return;
        setSnapshot(snap);
        setAuctionState(state);
        applyRoundSafe(state.currentRound);
      } else {
        const state = await getAuctionState(sid);
        if (gen !== fetchGenRef.current) return;
        setAuctionState(state);
        applyRoundSafe(state.currentRound);
      }
      setError(null);
      setSessionNotFound(false);
    } catch (e) {
      if (gen !== fetchGenRef.current) return;
      if (e instanceof ApiError && (e.status === 404 || e.status === 401)) {
        setSessionNotFound(true);
        setError(null);
      } else {
        setError(e instanceof Error ? e.message : "Sync failed");
      }
    } finally {
      fetchingRef.current = false;
      setIsRefreshing(false);
      const pending = pendingRefreshRef.current;
      pendingRefreshRef.current = "none";
      if (pending !== "none") {
        runRefresh(sid, pending);
      }
    }
  }, [applyRoundSafe]);

  const refreshState = useCallback((sid: string) => runRefresh(sid, "state"), [runRefresh]);
  const refreshFull = useCallback((sid: string) => runRefresh(sid, "full"), [runRefresh]);

  const dispatchEvents = useCallback(
    (envs: RealtimeEnvelope[], sid: string) => {
      for (const env of envs) {
        if (env.eventType === "heartbeat" || (env.seq ?? 0) === 0) {
          lastHeartbeatRef.current = Date.now();
        } else {
          lastEventTimeRef.current = Date.now();
          lastHeartbeatRef.current = Date.now();
          setLastEvent(env);
        }
      }

      const actionable = sortBySeq(
        envs.filter((e) => e.eventType !== "heartbeat" && (e.seq ?? 0) !== 0)
      );
      if (actionable.length === 0) return;

      let roundMismatch = false;
      const { result, lastSeq } = processEventBatch(actionable, lastServerSeqRef.current, {
        patchRound: (incoming, merge) => {
          setRound((prev) => {
            if (
              incoming.version != null &&
              incoming.version < lastMutationVersionRef.current
            ) {
              return prev;
            }
            if (prev && prev.roundId !== incoming.roundId) {
              roundMismatch = true;
              return incoming;
            }
            return merge && prev
              ? mergeRoundFromRest(prev, { ...prev, ...pickSharedFields(incoming) })
              : incoming;
          });
        },
        clearRound: () => setRound(null),
        setRound: (r) => {
          if (r.version != null && r.version < lastMutationVersionRef.current) {
            return;
          }
          setRound(r);
        },
        onMatchCompleted: () => setMatchCompleted(true),
      });

      if (lastSeq > lastServerSeqRef.current) {
        lastServerSeqRef.current = lastSeq;
      }

      if (result.matchCompleted) return;

      if (roundMismatch || result.seqGapDetected) {
        refreshState(sid);
      } else if (result.refreshKind === "full") {
        refreshFull(sid);
      } else if (result.refreshKind === "state") {
        refreshState(sid);
      }
    },
    [refreshFull, refreshState]
  );

  useEffect(() => {
    if (!sessionId) return;
    refreshFull(sessionId);
  }, [sessionId, refreshFull]);

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

  // WS alive (heartbeats/pong) but game events stalled while round OPEN → REST catch-up.
  useEffect(() => {
    if (!sessionId || sessionNotFound || matchCompleted || !isConnected || !roundIsOpen) return;

    const id = window.setInterval(() => {
      if (Date.now() - lastEventTimeRef.current > STALE_GAME_EVENT_MS) {
        refreshState(sessionId);
      }
    }, 4_000);

    return () => window.clearInterval(id);
  }, [sessionId, isConnected, roundIsOpen, sessionNotFound, matchCompleted, refreshState]);

  const prevConnectedRef = useRef(false);
  useEffect(() => {
    const was = prevConnectedRef.current;
    prevConnectedRef.current = isConnected;
    if (!sessionId || sessionNotFound || matchCompleted) return;

    if (isConnected && !was) {
      noteTransportActivity();
      const lastSeq = lastServerSeqRef.current;

      const replayPromise =
        lastSeq > 0
          ? fetchEventsSince(sessionId, lastSeq, spectator)
              .then(({ events: missed, lastSeq: serverLastSeq }) => {
                const action = decideReplayAction(lastSeq, serverLastSeq, missed.length);
                if (action === "replay") {
                  dispatchEvents(missed as RealtimeEnvelope[], sessionId);
                  lastServerSeqRef.current = Math.max(lastServerSeqRef.current, serverLastSeq);
                } else if (action === "full") {
                  refreshFull(sessionId);
                }
              })
              .catch(() => refreshFull(sessionId))
          : Promise.resolve();

      replayPromise.finally(() => refreshState(sessionId));
    }
  }, [isConnected, sessionId, sessionNotFound, matchCompleted, spectator, refreshFull, dispatchEvents, noteTransportActivity]);

  useEffect(() => {
    if (!sessionId || events.length === 0 || sessionNotFound || matchCompleted) return;

    const newEvents = drainUnseenEvents(events, seenEventIdsRef.current);
    if (newEvents.length === 0) return;
    dispatchEvents(newEvents, sessionId);
  }, [events, sessionId, sessionNotFound, matchCompleted, dispatchEvents]);

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
    applyActorRound,
    isConnected,
    lastEvent,
    error,
    sessionNotFound,
    matchCompleted,
    isRefreshing,
  };
}
