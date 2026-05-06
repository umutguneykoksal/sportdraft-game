import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AuctionRoundResponse, getSessionSnapshot, placeBid, startRound } from "../../shared/api";
import { useSessionStream } from "../../shared/sse";

export function AuctionPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const { events } = useSessionStream(sessionId ?? null);

  const [round, setRound] = useState<AuctionRoundResponse | null>(null);
  const [amount, setAmount] = useState<string>("200");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!sessionId) {
      return;
    }
    const timer = setInterval(async () => {
      try {
        const snapshot = await getSessionSnapshot(sessionId);
        if (snapshot.status === "FINISHED") {
          navigate(`/match/${sessionId}`);
        }
      } catch {
        // ignore polling issues
      }
    }, 1000);
    return () => clearInterval(timer);
  }, [sessionId, navigate]);

  useEffect(() => {
    const latest = events.at(-1);
    if (!latest) {
      return;
    }
    const payload = latest.payload as { round?: AuctionRoundResponse; sessionId?: string };
    if ((latest.eventType === "round_started" || latest.eventType === "bid_placed" || latest.eventType === "round_ended") && payload.round) {
      setRound(payload.round);
    }
    if (latest.eventType === "match_completed" && sessionId) {
      navigate(`/match/${sessionId}`);
    }
  }, [events, navigate, sessionId]);

  if (!sessionId) {
    return <div>Session id missing</div>;
  }

  const handleStartRound = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await startRound(sessionId);
      setRound(response);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to start round");
    } finally {
      setLoading(false);
    }
  };

  const handleBid = async () => {
    if (!round) {
      setError("Start a round first");
      return;
    }
    const amountCents = Number(amount);
    if (!Number.isFinite(amountCents) || amountCents <= 0) {
      setError("Amount must be a positive number (cents)");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const response = await placeBid(sessionId, round.roundId, amountCents);
      setRound(response);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to place bid");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ padding: 16, maxWidth: 900, margin: "0 auto", fontFamily: "Arial, sans-serif" }}>
      <h2>Auction</h2>
      <p>Session: {sessionId}</p>

      <button onClick={handleStartRound} disabled={loading}>
        {loading ? "Working..." : "Start Round"}
      </button>

      {round && (
        <div style={{ marginTop: 12 }}>
          <h4>Current Round</h4>
          <p>Round: {round.roundId}</p>
          <p>Card: {round.cardName} ({round.position})</p>
          <p>Highest Bid (cents): {round.highestBidCents}</p>
          <p>Ends At: {round.endsAt}</p>
          <p>Status: {round.status}</p>

          <input
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            type="number"
            min={1}
            style={{ marginRight: 8 }}
          />
          <button onClick={handleBid} disabled={loading}>
            Place Bid
          </button>
        </div>
      )}

      {error && <p style={{ color: "red" }}>{error}</p>}

      <h4 style={{ marginTop: 20 }}>Recent SSE Events</h4>
      <div style={{ background: "#f5f5f5", padding: 8, maxHeight: 320, overflow: "auto" }}>
        {events.slice().reverse().map((event, idx) => (
          <pre key={`${event.occurredAt}-${idx}`} style={{ whiteSpace: "pre-wrap", margin: 0 }}>
            {JSON.stringify(event, null, 2)}
          </pre>
        ))}
      </div>
    </div>
  );
}
