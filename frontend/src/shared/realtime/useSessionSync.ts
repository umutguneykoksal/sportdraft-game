import { useCallback, useEffect, useRef, useState } from "react";
import { AuctionRoundResponse, AuctionStateResponse, SessionSnapshotResponse } from "../api/contracts";
import { getAuctionState, getSessionSnapshot } from "../api";
import { fetchEventsSince } from "../api/sessionClient";
import { ApiError } from "../api/errors";
import {
  decideReplayAction,
  mergeRoundFromRest,
  pickSharedFields,
  processEventBatch,
  RefreshKind,
} from "./eventDispatch";
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
  isRefreshing: boolean;
}

const POLL_INTERVAL_IDLE_MS = 30_000;
const POLL_INTERVAL_ROUND_OPEN_MS = 5_000;
const POLL_INTERVAL_DISCONNECTED_MS = 2_000;
const VISIBILITY_REFRESH_THRESHOLD_MS = 10_000;
const HEARTBEAT_TIMEOUT_MS = 45_000;

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

  const { events, isConnected } = useRealtimeSession(sessionId, spectator, reconnectKey);

  const fetchingRef = useRef(false);
  const pendingRefreshRef = useRef<RefreshKind>("none");
  const fetchGenRef = useRef(0);
  const lastEventIdxRef = useRef(-1);
  const lastServerSeqRef = useRef(0);
  const lastEventTimeRef = useRef(Date.now());
  const lastHeartbeatRef = useRef(Date.now());
  const roundRef = useRef<AuctionRoundResponse | null>(null);

  useEffect(() => {
    roundRef.current = round;
  }, [round]);

  const forceReconnect = useCallback(() => {
    setReconnectKey((k) => k + 1);
  }, []);

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
    lastEventIdxRef.current = -1;
    lastServerSeqRef.current = 0;
    lastEventTimeRef.current = Date.now();
    lastHeartbeatRef.current = Date.now();
  }, [sessionId]);

  const applyRoundSafe = useCallback((incoming: AuctionRoundResponse | null) => {
    setRound((current) => mergeRoundFromRest(current, incoming));
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

      const actionable = envs.filter(
        (e) => e.eventType !== "heartbeat" && (e.seq ?? 0) !== 0
      );
      if (actionable.length === 0) return;

      const { result, lastSeq } = processEventBatch(actionable, lastServerSeqRef.current, {
        patchRound: (incoming, merge) => {
          setRound((prev) => {
            if (prev && prev.roundId !== incoming.roundId) return prev;
            return merge && prev
              ? mergeRoundFromRest(prev, { ...prev, ...pickSharedFields(incoming) })
              : incoming;
          });
        },
        clearRound: () => setRound(null),
        setRound: (r) => setRound(r),
        onMatchCompleted: () => setMatchCompleted(true),
      });

      if (lastSeq > lastServerSeqRef.current) {
        lastServerSeqRef.current = lastSeq;
      }

      if (result.matchCompleted) return;

      if (result.refreshKind === "full") {
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

  const prevConnectedRef = useRef(false);
  useEffect(() => {
    const was = prevConnectedRef.current;
    prevConnectedRef.current = isConnected;
    if (!sessionId || sessionNotFound || matchCompleted) return;

    if (isConnected && !was) {
      const lastSeq = lastServerSeqRef.current;
      if (lastSeq === 0) return;

      fetchEventsSince(sessionId, lastSeq, spectator)
        .then(({ events: missed, lastSeq: serverLastSeq }) => {
          const action = decideReplayAction(lastSeq, serverLastSeq, missed.length);
          if (action === "none") return;
          if (action === "full") {
            refreshFull(sessionId);
            return;
          }
          dispatchEvents(missed as RealtimeEnvelope[], sessionId);
          lastServerSeqRef.current = Math.max(lastServerSeqRef.current, serverLastSeq);
        })
        .catch(() => refreshFull(sessionId));
    }
  }, [isConnected, sessionId, sessionNotFound, matchCompleted, spectator, refreshFull, dispatchEvents]);

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
    isConnected,
    lastEvent,
    error,
    sessionNotFound,
    matchCompleted,
    isRefreshing,
  };
}
