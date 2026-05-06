import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useSessionStream } from "../../shared/sse";

export function MatchPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const { events } = useSessionStream(sessionId ?? null);
  const [homeScore, setHomeScore] = useState<number | null>(null);
  const [awayScore, setAwayScore] = useState<number | null>(null);

  useEffect(() => {
    const latest = events
      .slice()
      .reverse()
      .find((event) => event.eventType === "match_completed");
    if (!latest) {
      return;
    }

    const payload = latest.payload as { homeScore?: number; awayScore?: number };
    if (typeof payload.homeScore === "number") {
      setHomeScore(payload.homeScore);
    }
    if (typeof payload.awayScore === "number") {
      setAwayScore(payload.awayScore);
    }
  }, [events]);

  const ready = useMemo(
    () => typeof homeScore === "number" && typeof awayScore === "number",
    [homeScore, awayScore]
  );

  return (
    <div style={{ padding: 16, maxWidth: 700, margin: "0 auto", fontFamily: "Arial, sans-serif" }}>
      <h2>Match Result</h2>
      <p>Session: {sessionId}</p>
      {!ready ? <p>Waiting for match result event...</p> : <h3>Home {homeScore} - {awayScore} Away</h3>}

      <button onClick={() => navigate("/lobby")}>Back to Lobby</button>
    </div>
  );
}
