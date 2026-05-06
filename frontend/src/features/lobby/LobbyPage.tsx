import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Formation,
  SessionSnapshotResponse,
  getSessionSnapshot,
  joinLobby,
  leaveLobby,
  selectFormation,
} from "../../shared/api";
import {
  clearActiveSessionId,
  getActiveSessionId,
  getOrCreateClientSession,
  setActiveSessionId,
} from "../../shared/session";
import { useSessionStream } from "../../shared/sse";

const formations: Formation[] = ["FOUR_FOUR_TWO", "FOUR_THREE_THREE", "THREE_FIVE_TWO"];

export function LobbyPage() {
  const navigate = useNavigate();
  const [snapshot, setSnapshot] = useState<SessionSnapshotResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const session = getOrCreateClientSession();
  const sessionId = snapshot?.sessionId ?? null;
  const { events } = useSessionStream(sessionId);

  useEffect(() => {
    const restore = async () => {
      const activeSessionId = getActiveSessionId();
      if (!activeSessionId || snapshot) {
        return;
      }
      try {
        const restored = await getSessionSnapshot(activeSessionId);
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
    if (snapshot.status === "AUCTIONING") {
      navigate(`/auction/${snapshot.sessionId}`);
    }
  }, [snapshot, navigate]);

  useEffect(() => {
    if (!snapshot) {
      return;
    }
    const timer = setInterval(async () => {
      try {
        const latest = await getSessionSnapshot(snapshot.sessionId);
        setSnapshot(latest);
      } catch {
        // ignore polling errors for now
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [snapshot]);

  const handleJoin = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await joinLobby(session.nickname);
      setSnapshot(response);
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
      const response = await selectFormation(snapshot.sessionId, formation);
      setSnapshot(response);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to select formation");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ padding: 16, maxWidth: 800, margin: "0 auto", fontFamily: "Arial, sans-serif" }}>
      <h2>Lobby</h2>
      <p>Nickname: {session.nickname}</p>
      {!snapshot && (
        <button onClick={handleJoin} disabled={loading}>
          {loading ? "Joining..." : "Join Lobby"}
        </button>
      )}

      {snapshot && (
        <div>
          <p>Session: {snapshot.sessionId}</p>
          <p>Status: {snapshot.status}</p>
          <p>Players: {snapshot.playerCount}/2</p>
          <p>Countdown Ends: {snapshot.countdownEndsAt ?? "-"}</p>
          <p>My Formation: {snapshot.myFormation ?? "Not selected"}</p>

          <button onClick={handleLeave} disabled={loading}>
            Leave Lobby
          </button>

          {snapshot.status === "FORMATION_SELECT" && (
            <div style={{ marginTop: 12 }}>
              <h4>Select Formation</h4>
              {formations.map((formation) => (
                <button
                  key={formation}
                  onClick={() => chooseFormation(formation)}
                  disabled={loading}
                  style={{ marginRight: 8 }}
                >
                  {formation}
                </button>
              ))}
            </div>
          )}
        </div>
      )}

      {error && <p style={{ color: "red" }}>{error}</p>}

      <h4 style={{ marginTop: 20 }}>Recent SSE Events</h4>
      <div style={{ background: "#f5f5f5", padding: 8, maxHeight: 240, overflow: "auto" }}>
        {events.slice().reverse().map((event, idx) => (
          <pre key={`${event.occurredAt}-${idx}`} style={{ whiteSpace: "pre-wrap", margin: 0 }}>
            {JSON.stringify(event, null, 2)}
          </pre>
        ))}
      </div>
    </div>
  );
}
