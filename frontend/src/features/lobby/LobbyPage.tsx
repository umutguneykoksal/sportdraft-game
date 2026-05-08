import { useLobbyFlow } from "./hooks/useLobbyFlow";

export function LobbyPage() {
  const {
    session,
    snapshot,
    loading,
    error,
    isConnected,
    countdownSecondsLeft,
    formations,
    handleJoin,
    handleLeave,
    chooseFormation,
  } = useLobbyFlow();

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
          <p>Countdown: {countdownSecondsLeft !== null ? `${countdownSecondsLeft}s` : "-"}</p>
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
      {snapshot && !isConnected && (
        <p style={{ color: "#a15c00" }}>Reconnecting live updates...</p>
      )}

    </div>
  );
}
