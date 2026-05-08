import { useEffect } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMatchFlow } from "./hooks/useMatchFlow";

export function MatchPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const { ready, homeScore, awayScore } = useMatchFlow(sessionId);

  return (
    <div style={{ padding: 16, maxWidth: 700, margin: "0 auto", fontFamily: "Arial, sans-serif" }}>
      <h2>Match Result</h2>
      <p>Session: {sessionId}</p>
      {!ready ? <p>Waiting for match result event...</p> : <h3>Home {homeScore} - {awayScore} Away</h3>}

      <button onClick={() => navigate("/lobby")}>Back to Lobby</button>
    </div>
  );
}
