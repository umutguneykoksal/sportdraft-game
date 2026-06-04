import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Formation,
  GameMode,
  LobbyRoomResponse,
  PoolMinRating,
  PoolLeagueId,
  PoolMinRatingOption,
  SessionSnapshotResponse,
  getSessionSnapshot,
  createRoom,
  joinRoom,
  leaveLobby,
  listLobbies,
  getPoolMinRatingOptions,
  selectFormation,
  confirmFormationReady,
} from "../../../shared/api";
import {
  clearActiveSessionId,
  getActiveSessionId,
  getOrCreateClientSession,
  setActiveSessionId,
} from "../../../shared/session";
import { useSessionStream } from "../../../shared/sse";
import { drainUnseenEvents } from "../../../shared/realtime/eventDispatch";
import { LOBBY_PHASE_POLL_MS } from "../../../shared/realtime/syncConstants";
import { ALL_FORMATIONS } from "../../../shared/formations";
import { useLobbyCountdownSeconds } from "./useLobbyCountdownSeconds";
import { shouldPollLobbyPhase, shouldRefetchLobbySnapshot } from "./lobbySnapshotSync";

const LOBBY_POLL_INTERVAL_MS = 3000;

export function useLobbyFlow() {
  const navigate = useNavigate();
  const [snapshot, setSnapshot] = useState<SessionSnapshotResponse | null>(null);
  const [rooms, setRooms] = useState<LobbyRoomResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [createGameMode, setCreateGameMode] = useState<GameMode>("DUEL");
  const [createMinPoolRating, setCreateMinPoolRating] = useState<PoolMinRating>(70);
  const [createPoolLeague, setCreatePoolLeague] = useState<PoolLeagueId>("PREMIER_LEAGUE");
  const [poolMinRatingOptions, setPoolMinRatingOptions] = useState<PoolMinRatingOption[]>([]);
  const [poolMinRatingOptionsLoaded, setPoolMinRatingOptionsLoaded] = useState(false);
  const session = getOrCreateClientSession();
  const sessionId = snapshot?.sessionId ?? null;
  const { events, isConnected } = useSessionStream(sessionId);
  const restoredRef = useRef(false);
  const prevConnectedRef = useRef(false);
  const wasEverConnectedRef = useRef(false);
  const seenEventIdsRef = useRef(new Set<string>());
  const sessionIdRef = useRef(sessionId);
  sessionIdRef.current = sessionId;
  const syncSeqRef = useRef(0);

  const reportSyncError = useCallback((e: unknown, fallback: string) => {
    setError(e instanceof Error ? e.message : fallback);
  }, []);

  const refetchSnapshot = useCallback((sid: string) => {
    const seq = ++syncSeqRef.current;
    return getSessionSnapshot(sid)
      .then((snap) => {
        if (seq === syncSeqRef.current && sessionIdRef.current === sid) {
          setSnapshot(snap);
        }
      })
      .catch((e) => reportSyncError(e, "Could not refresh lobby state"));
  }, [reportSyncError]);

  useEffect(() => {
    seenEventIdsRef.current.clear();
    wasEverConnectedRef.current = false;
  }, [sessionId]);

  useEffect(() => {
    if (isConnected) wasEverConnectedRef.current = true;
  }, [isConnected]);

  const showReconnecting = !!sessionId && wasEverConnectedRef.current && !isConnected;

  // 1. Restore session on mount
  useEffect(() => {
    if (restoredRef.current || snapshot) return;
    restoredRef.current = true;
    const activeSessionId = getActiveSessionId();
    if (!activeSessionId) return;

    (async () => {
      try {
        const restored = await getSessionSnapshot(activeSessionId);
        const terminal = restored.status === "FINISHED"
          || restored.status === "AWAITING_RESULT"
          || restored.status === "FINALIZING";
        if (terminal) {
          clearActiveSessionId();
          return;
        }
        if (!restored.myFormation) {
          try { await leaveLobby(activeSessionId); } catch { /* best-effort */ }
          clearActiveSessionId();
          return;
        }
        setSnapshot(restored);
      } catch {
        clearActiveSessionId();
      }
    })();
  }, [snapshot]);

  // 2. Persist / clear active session in localStorage
  useEffect(() => {
    if (!snapshot) return;
    if (snapshot.joined) {
      setActiveSessionId(snapshot.sessionId);
    } else {
      clearActiveSessionId();
    }
  }, [snapshot]);

  // 3. Navigate to auction when session advances
  useEffect(() => {
    if (!snapshot) return;
    if (snapshot.status === "AUCTIONING" || snapshot.status === "MATCH_START_PENDING") {
      navigate(`/auction/${snapshot.sessionId}`);
    }
  }, [snapshot, navigate]);

  // 4. SSE reconnection catch-up
  useEffect(() => {
    const wasConnected = prevConnectedRef.current;
    prevConnectedRef.current = isConnected;
    const sid = sessionIdRef.current;
    if (!sid || !isConnected || !wasConnected) return;
    refetchSnapshot(sid).catch(() => { /* error surfaced via reportSyncError */ });
  }, [isConnected, refetchSnapshot]);

  // 5. SSE — drain all unseen events (not only the last buffer index)
  useEffect(() => {
    if (events.length === 0) return;
    const fresh = drainUnseenEvents(events, seenEventIdsRef.current);
    if (fresh.length === 0) return;

    const sid = sessionIdRef.current;
    if (!sid || !shouldRefetchLobbySnapshot(fresh)) return;

    refetchSnapshot(sid).catch(() => { /* error surfaced via reportSyncError */ });
  }, [events, refetchSnapshot]);

  // 5b. Poll snapshot while in lobby phases (catches missed auctioning_started)
  useEffect(() => {
    const sid = snapshot?.sessionId;
    const status = snapshot?.status;
    if (!sid || !shouldPollLobbyPhase(status)) return;

    refetchSnapshot(sid).catch(() => {});
    const interval = window.setInterval(() => {
      refetchSnapshot(sid).catch(() => {});
    }, LOBBY_PHASE_POLL_MS);
    return () => window.clearInterval(interval);
  }, [snapshot?.sessionId, snapshot?.status, refetchSnapshot]);

  // 5c. Tab focus — catch up after background / flaky transport
  useEffect(() => {
    const onVisible = () => {
      if (document.visibilityState !== "visible") return;
      const sid = sessionIdRef.current;
      if (!sid) return;
      refetchSnapshot(sid).catch(() => {});
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [refetchSnapshot]);

  const countdownSecondsLeft = useLobbyCountdownSeconds(snapshot);

  // 7. Lobby list polling (only when not in a session)
  useEffect(() => {
    if (snapshot) return;
    let cancelled = false;
    const poll = async () => {
      try {
        const list = await listLobbies();
        if (!cancelled) setRooms(list);
      } catch (e) {
        if (!cancelled) reportSyncError(e, "Could not load lobby list");
      }
    };
    poll();
    const interval = setInterval(poll, LOBBY_POLL_INTERVAL_MS);
    return () => { cancelled = true; clearInterval(interval); };
  }, [snapshot, reportSyncError]);

  // 7b. Pool min-rating options for selected league (disable thresholds with too few cards)
  useEffect(() => {
    let cancelled = false;
    setPoolMinRatingOptionsLoaded(false);
    (async () => {
      try {
        const res = await getPoolMinRatingOptions(createPoolLeague);
        if (cancelled) return;
        setPoolMinRatingOptions(res.options);
        setPoolMinRatingOptionsLoaded(true);
        setCreateMinPoolRating((current) => {
          const currentOption = res.options.find((o) => o.minRating === current);
          if (currentOption?.selectable) return current;
          const firstSelectable = res.options.find((o) => o.selectable);
          return (firstSelectable?.minRating ?? 70) as PoolMinRating;
        });
      } catch {
        if (!cancelled) {
          setPoolMinRatingOptions([]);
          setPoolMinRatingOptionsLoaded(true);
        }
      }
    })();
    return () => { cancelled = true; };
  }, [createPoolLeague]);

  // --- Handlers ---

  const handleCreateRoom = useCallback(async () => {
    const fresh = getOrCreateClientSession();
    setLoading(true);
    setError(null);
    try {
      setSnapshot(await createRoom(fresh.nickname, createGameMode, createMinPoolRating, createPoolLeague));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to create room");
    } finally {
      setLoading(false);
    }
  }, [createGameMode, createMinPoolRating, createPoolLeague]);

  const handleJoinRoom = useCallback(async (roomSessionId: string) => {
    const fresh = getOrCreateClientSession();
    setLoading(true);
    setError(null);
    try {
      setSnapshot(await joinRoom(roomSessionId, fresh.nickname));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to join room");
    } finally {
      setLoading(false);
    }
  }, []);

  const handleLeave = useCallback(async () => {
    if (!snapshot) return;
    setLoading(true);
    setError(null);
    try {
      ++syncSeqRef.current;
      await leaveLobby(snapshot.sessionId);
      clearActiveSessionId();
      setSnapshot(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to leave lobby");
    } finally {
      setLoading(false);
    }
  }, [snapshot]);

  const handleStartAuction = useCallback(async () => {
    if (!snapshot) return;
    setLoading(true);
    setError(null);
    try {
      setSnapshot(await confirmFormationReady(snapshot.sessionId));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to start auction");
    } finally {
      setLoading(false);
    }
  }, [snapshot]);

  const chooseFormation = useCallback(async (formation: Formation) => {
    if (!snapshot || loading || snapshot.myFormation === formation) return;
    setLoading(true);
    setError(null);
    try {
      setSnapshot(await selectFormation(snapshot.sessionId, formation));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to select formation");
    } finally {
      setLoading(false);
    }
  }, [snapshot, loading]);

  return {
    session,
    snapshot,
    rooms,
    loading,
    error,
    isConnected,
    showReconnecting,
    countdownSecondsLeft,
    formations: ALL_FORMATIONS,
    handleCreateRoom,
    handleJoinRoom,
    handleLeave,
    handleStartAuction,
    chooseFormation,
    createGameMode,
    setCreateGameMode,
    createMinPoolRating,
    setCreateMinPoolRating,
    createPoolLeague,
    setCreatePoolLeague,
    poolMinRatingOptions,
    poolMinRatingOptionsLoaded,
  };
}
