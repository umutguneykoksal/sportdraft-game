import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Formation,
  SessionSnapshotResponse,
  getSessionSnapshot,
  joinLobby,
  leaveLobby,
  selectFormation,
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
  const { isConnected } = useSessionStream(sessionId);

  useEffect(() => {
    const restore = async () => {
      const activeSessionId = getActiveSessionId();
      if (!activeSessionId || snapshot) {
        return;
      }
      try {
        const restored = await getSessionSnapshot(activeSessionId);
        const terminal = restored.status === "FINISHED"
          || restored.status === "AWAITING_RESULT"
          || restored.status === "FINALIZING";
        if (terminal) {
          clearActiveSessionId();
          return;
        }
        setSnapshot(restored);
      } catch {
        clearActiveSessionId();
      }
    };
    restore();
  }, [snapshot]);

  useEffect(() => {
    if (!snapshot) {
      return;
    }
    if (snapshot.joined) {
      setActiveSessionId(snapshot.sessionId);
    } else {
      clearActiveSessionId();
    }
  }, [snapshot]);

  useEffect(() => {
    if (!snapshot) {
      return;
    }
    if (snapshot.status === "AUCTIONING" || snapshot.status === "MATCH_START_PENDING") {
      navigate(`/auction/${snapshot.sessionId}`);
    }
  }, [snapshot, navigate]);

  useEffect(() => {
    if (!snapshot || !isConnected) {
      return;
    }
    getSessionSnapshot(snapshot.sessionId).then(setSnapshot).catch(() => {});
  }, [snapshot, isConnected]);

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

  const handleJoin = async () => {
    setLoading(true);
    setError(null);
    try {
      setSnapshot(await joinLobby(session.nickname));
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

  const chooseFormation = async (formation: Formation) => {
    if (!snapshot) {
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
    chooseFormation,
  };
}
