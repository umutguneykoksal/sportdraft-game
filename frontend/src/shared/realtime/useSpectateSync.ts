import { useCallback, useEffect, useRef, useState } from "react";
import { AuctionRoundResponse, SpectateStateResponse } from "../api/contracts";
import { getSpectateState } from "../api/lobbyClient";
import { fetchEventsSince } from "../api/sessionClient";
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

const POLL_INTERVAL_IDLE_MS = 30_000;
const POLL_INTERVAL_ROUND_OPEN_MS = 5_000;
const POLL_INTERVAL_DISCONNECTED_MS = 2_000;
const VISIBILITY_REFRESH_THRESHOLD_MS = 10_000;
const HEARTBEAT_TIMEOUT_MS = 90_000;

export function useSpectateSync(sessionId: string | null) {
  const [state, setState] = useState<SpectateStateResponse | null>(null);
  const [round, setRound] = useState<AuctionRoundResponse | null>(null);
  const [lastEvent, setLastEvent] = useState<RealtimeEnvelope | null>(null);
  const [matchCompleted, setMatchCompleted] = useState(false);
  const [reconnectKey, setReconnectKey] = useState(0);
  const [loadFailed, setLoadFailed] = useState(false);

  const fetchingRef = useRef(false);
  const pendingRefreshRef = useRef<RefreshKind>("none");
  const fetchGenRef = useRef(0);
  const lastServerSeqRef = useRef(0);
  const lastEventTimeRef = useRef(Date.now());
  const lastHeartbeatRef = useRef(Date.now());
  const hasLoadedRef = useRef(false);
  const seenEventIdsRef = useRef(new Set<string>());

  const noteTransportActivity = useCallback(() => {
    lastHeartbeatRef.current = Date.now();
  }, []);

  const forceReconnect = useCallback(() => {
    setReconnectKey((k) => k + 1);
  }, []);

  const { events, isConnected } = useRealtimeSession(sessionId, true, reconnectKey, noteTransportActivity);

  const applySpectateState = useCallback((s: SpectateStateResponse) => {
    setState(s);
    setRound((prev) => mergeRoundFromRest(prev, s.currentRound));
  }, []);

  useEffect(() => {
    setState(null);
    setRound(null);
    setLastEvent(null);
    setMatchCompleted(false);
    setLoadFailed(false);
    fetchingRef.current = false;
    pendingRefreshRef.current = "none";
    fetchGenRef.current = 0;
    lastServerSeqRef.current = 0;
    lastEventTimeRef.current = Date.now();
    lastHeartbeatRef.current = Date.now();
    hasLoadedRef.current = false;
    seenEventIdsRef.current.clear();
  }, [sessionId]);

  const runRefresh = useCallback(async (sid: string) => {
    if (fetchingRef.current) {
      pendingRefreshRef.current = "full";
      return;
    }
    fetchingRef.current = true;
    const gen = ++fetchGenRef.current;
    try {
      const s = await getSpectateState(sid);
      if (gen !== fetchGenRef.current) return;
      applySpectateState(s);
      hasLoadedRef.current = true;
      setLoadFailed(false);
    } catch {
      if (gen === fetchGenRef.current && !hasLoadedRef.current) {
        setLoadFailed(true);
      }
    } finally {
      fetchingRef.current = false;
      if (pendingRefreshRef.current !== "none") {
        pendingRefreshRef.current = "none";
        runRefresh(sid);
      }
    }
  }, [applySpectateState]);

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

      const { result, lastSeq } = processEventBatch(
        actionable,
        lastServerSeqRef.current,
        {
          patchRound: (incoming, merge) => {
            setRound((prev) => {
              if (prev && prev.roundId !== incoming.roundId) return incoming;
              return merge && prev
                ? mergeRoundFromRest(prev, { ...prev, ...pickSharedFields(incoming) }) ?? prev
                : incoming;
            });
          },
          clearRound: () => setRound(null),
          setRound: (r) => setRound(r),
          onMatchCompleted: () => {
            setMatchCompleted(true);
            runRefresh(sid);
          },
        },
        { spectate: true }
      );

      if (lastSeq > lastServerSeqRef.current) {
        lastServerSeqRef.current = lastSeq;
      }

      if (result.matchCompleted) return;

      if (result.refreshKind === "full" || result.refreshKind === "state") {
        runRefresh(sid);
      }
    },
    [runRefresh]
  );

  useEffect(() => {
    if (!sessionId) return;
    runRefresh(sessionId);
  }, [sessionId, runRefresh]);

  const roundIsOpen = round?.status === "OPEN";

  useEffect(() => {
    if (!sessionId || matchCompleted) return;
    const interval = !isConnected
      ? POLL_INTERVAL_DISCONNECTED_MS
      : roundIsOpen
        ? POLL_INTERVAL_ROUND_OPEN_MS
        : POLL_INTERVAL_IDLE_MS;
    const id = window.setInterval(() => runRefresh(sessionId), interval);
    return () => window.clearInterval(id);
  }, [sessionId, isConnected, roundIsOpen, matchCompleted, runRefresh]);

  const prevConnectedRef = useRef(false);
  useEffect(() => {
    const was = prevConnectedRef.current;
    prevConnectedRef.current = isConnected;
    if (!sessionId || matchCompleted) return;

    if (isConnected && !was) {
      noteTransportActivity();
      const lastSeq = lastServerSeqRef.current;

      const replayPromise =
        lastSeq > 0
          ? fetchEventsSince(sessionId, lastSeq, true)
              .then(({ events: missed, lastSeq: serverLastSeq }) => {
                const action = decideReplayAction(lastSeq, serverLastSeq, missed.length);
                if (action === "replay") {
                  dispatchEvents(missed as RealtimeEnvelope[], sessionId);
                  lastServerSeqRef.current = Math.max(lastServerSeqRef.current, serverLastSeq);
                } else if (action === "full") {
                  runRefresh(sessionId);
                }
              })
              .catch(() => runRefresh(sessionId))
          : Promise.resolve();

      replayPromise.finally(() => runRefresh(sessionId));
    }
  }, [isConnected, sessionId, matchCompleted, runRefresh, dispatchEvents, noteTransportActivity]);

  useEffect(() => {
    if (!sessionId || events.length === 0 || matchCompleted) return;

    const newEvents = drainUnseenEvents(events, seenEventIdsRef.current);
    if (newEvents.length === 0) return;
    dispatchEvents(newEvents, sessionId);
  }, [events, sessionId, matchCompleted, dispatchEvents]);

  useEffect(() => {
    if (!sessionId || matchCompleted) return;

    const handleVisibility = () => {
      if (document.visibilityState !== "visible") return;
      const gap = Date.now() - lastEventTimeRef.current;
      if (gap > VISIBILITY_REFRESH_THRESHOLD_MS) {
        runRefresh(sessionId);
      }
      if (!isConnected) {
        forceReconnect();
      }
    };

    document.addEventListener("visibilitychange", handleVisibility);
    return () => document.removeEventListener("visibilitychange", handleVisibility);
  }, [sessionId, isConnected, matchCompleted, runRefresh, forceReconnect]);

  useEffect(() => {
    if (!sessionId || matchCompleted || !isConnected) return;

    const id = window.setInterval(() => {
      if (Date.now() - lastHeartbeatRef.current > HEARTBEAT_TIMEOUT_MS) {
        forceReconnect();
      }
    }, 10_000);

    return () => window.clearInterval(id);
  }, [sessionId, isConnected, matchCompleted, forceReconnect]);

  return { state, round, lastEvent, isConnected, matchCompleted, loadFailed };
}
