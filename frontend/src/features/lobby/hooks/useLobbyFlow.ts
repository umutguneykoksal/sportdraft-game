import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Formation,
  SessionSnapshotResponse,
  getSessionSnapshot,
  joinLobby,
  leaveLobby,
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
import { ALL_FORMATIONS } from "../../../shared/formations";

export function useLobbyFlow() {
  const navigate = useNavigate();
  const [snapshot, setSnapshot] = useState<SessionSnapshotResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [countdownSecondsLeft, setCountdownSecondsLeft] = useState<number | null>(null);
  const session = getOrCreateClientSession();
  const sessionId = snapshot?.sessionId ?? null;
  const { events, isConnected } = useSessionStream(sessionId);
  const restoredRef = useRef(false);
  const prevConnectedRef = useRef(false);
  const lastProcessedEventIdx = useRef(-1);
  const sessionIdRef = useRef(sessionId);
  sessionIdRef.current = sessionId;

  // ---- Effects ----

  // 1. Restore session on mount (dissolve if no formation was selected)
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

  // 4. SSE reconnection catch-up (skips initial connect, only fires on re-connect)
  useEffect(() => {
    const wasConnected = prevConnectedRef.current;
    prevConnectedRef.current = isConnected;
    const sid = sessionIdRef.current;
    if (!sid || !isConnected || !wasConnected) return;
    getSessionSnapshot(sid).then(setSnapshot).catch(() => {});
  }, [isConnected]);

  // 5. SSE event handler (real-time lobby updates)
  useEffect(() => {
    if (events.length === 0) return;
    const idx = events.length - 1;
    if (idx <= lastProcessedEventIdx.current) return;
    lastProcessedEventIdx.current = idx;

    const sid = sessionIdRef.current;
    if (!sid) return;

    const latest = events[idx];
    const refetchEvents = [
      "lobby_updated", "countdown_started", "countdown_cancelled",
      "formation_phase_started", "formation_ready", "auctioning_started",
    ];
    if (refetchEvents.includes(latest.eventType)) {
      getSessionSnapshot(sid).then(setSnapshot).catch(() => {});
    }
  }, [events]);

  // 6. Countdown timer
  useEffect(() => {
    const deadlineAt = snapshot?.countdownEndsAt;
    if (!deadlineAt) {
      setCountdownSecondsLeft(null);
      return;
    }
    const compute = () => Math.max(0, Math.ceil((new Date(deadlineAt).getTime() - Date.now()) / 1000));
    setCountdownSecondsLeft(compute());
    const timer = setInterval(() => setCountdownSecondsLeft(compute()), 250);
    return () => clearInterval(timer);
  }, [snapshot?.countdownEndsAt]);

  // ---- Handlers ----

  const handleJoin = async () => {
    const fresh = getOrCreateClientSession();
    setLoading(true);
    setError(null);
    try {
      setSnapshot(await joinLobby(fresh.nickname));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to join lobby");
    } finally {
      setLoading(false);
    }
  };

  const handleLeave = async () => {
    if (!snapshot) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await leaveLobby(snapshot.sessionId);
      setSnapshot(response);
      if (!response.joined) {
        clearActiveSessionId();
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to leave lobby");
    } finally {
      setLoading(false);
    }
  };

  const handleStartAuction = async () => {
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
  };

  const chooseFormation = async (formation: Formation) => {
    if (!snapshot || loading || snapshot.myFormation === formation) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setSnapshot(await selectFormation(snapshot.sessionId, formation));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to select formation");
    } finally {
      setLoading(false);
    }
  };

  return {
    session,
    snapshot,
    loading,
    error,
    isConnected,
    countdownSecondsLeft,
    formations: ALL_FORMATIONS,
    handleJoin,
    handleLeave,
    handleStartAuction,
    chooseFormation,
  };
}
