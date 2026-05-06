import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { AuctionRoundResponse, getSessionSnapshot, placeBid, startRound } from "../../shared/api";
import { clearActiveSessionId, setActiveSessionId } from "../../shared/session";
import { useSessionStream } from "../../shared/sse";

interface WonCard {
  roundId: string;
  cardName: string;
  position: string;
  amountCents: number;
}

export function AuctionPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const { events, isConnected } = useSessionStream(sessionId ?? null);
  const myUserId = localStorage.getItem("sessionToken");

  const [round, setRound] = useState<AuctionRoundResponse | null>(null);
  const [amount, setAmount] = useState<string>("1");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [myTeam, setMyTeam] = useState<WonCard[]>([]);
  const [opponentTeam, setOpponentTeam] = useState<WonCard[]>([]);
  const [secondsLeft, setSecondsLeft] = useState<number>(0);
  const processedRoundsRef = useRef<Set<string>>(new Set());
  const isMyTurn = !!myUserId && !!round && round.currentTurnUserId === myUserId;

  useEffect(() => {
    if (!sessionId) {
      return;
    }
    setActiveSessionId(sessionId);
    let cancelled = false;
    const syncSnapshot = async () => {
      try {
        const snapshot = await getSessionSnapshot(sessionId);
        if (cancelled) {
          return;
        }
        if (snapshot.status === "FINISHED") {
          navigate(`/match/${sessionId}`);
          return;
        }
        if (snapshot.status !== "AUCTIONING" && snapshot.status !== "AWAITING_RESULT") {
          navigate("/lobby");
        }
      } catch {
        if (!cancelled) {
          clearActiveSessionId();
          navigate("/lobby");
        }
      }
    };
    syncSnapshot();
    return () => {
      cancelled = true;
    };
  }, [sessionId, navigate]);

  useEffect(() => {
    if (!sessionId || !isConnected) {
      return;
    }
    const refresh = async () => {
      try {
        const snapshot = await getSessionSnapshot(sessionId);
        if (snapshot.status === "FINISHED") {
          navigate(`/match/${sessionId}`);
          return;
        }
        if (snapshot.status !== "AUCTIONING" && snapshot.status !== "AWAITING_RESULT") {
          navigate("/lobby");
        }
      } catch {
        // ignore sync issues
      }
    };
    refresh();
  }, [isConnected, navigate, sessionId]);

  useEffect(() => {
    const latest = events.at(-1);
    if (!latest) {
      return;
    }
    const payload = latest.payload as {
      round?: AuctionRoundResponse;
      sessionId?: string;
      result?: string;
      winnerUserId?: string;
    };
    if ((latest.eventType === "round_started" || latest.eventType === "bid_placed" || latest.eventType === "round_ended") && payload.round) {
      setRound(payload.round);
    }
    if (
      latest.eventType === "round_ended" &&
      payload.result === "SOLD" &&
      payload.round &&
      payload.winnerUserId
    ) {
      const endedRoundId = payload.round.roundId;
      if (!processedRoundsRef.current.has(endedRoundId)) {
        processedRoundsRef.current.add(endedRoundId);
        const wonCard: WonCard = {
          roundId: endedRoundId,
          cardName: payload.round.cardName,
          position: payload.round.position,
          amountCents: payload.round.highestBidCents,
        };
        if (payload.winnerUserId === myUserId) {
          setMyTeam((prev) => [...prev, wonCard]);
        } else {
          setOpponentTeam((prev) => [...prev, wonCard]);
        }
      }
    }
    if (latest.eventType === "match_completed" && sessionId) {
      navigate(`/match/${sessionId}`);
    }
  }, [events, myUserId, navigate, sessionId]);

  useEffect(() => {
    if (!round) {
      return;
    }
    const minimum = Math.max(1, round.highestBidCents + 1);
    setAmount((prev) => {
      const parsed = Number(prev);
      if (!Number.isFinite(parsed) || parsed < minimum) {
        return String(minimum);
      }
      return prev;
    });
  }, [round]);

  useEffect(() => {
    if (!round) {
      setSecondsLeft(0);
      return;
    }
    const computeSecondsLeft = () => {
      const diffMs = new Date(round.endsAt).getTime() - Date.now();
      return Math.max(0, Math.ceil(diffMs / 1000));
    };
    setSecondsLeft(computeSecondsLeft());
    const timer = setInterval(() => {
      setSecondsLeft(computeSecondsLeft());
    }, 250);
    return () => clearInterval(timer);
  }, [round]);

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
    if (!isMyTurn) {
      setError("It is not your turn");
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
          <p>Time Left: {secondsLeft}s</p>
          <p>Status: {round.status}</p>

          <input
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            type="number"
            min={1}
            placeholder="Enter bid (cents)"
            disabled={loading || !isMyTurn}
            style={{ marginRight: 8 }}
          />
          <button
            onClick={() => {
              const current = Number(amount);
              const safe = Number.isFinite(current) ? current : Math.max(1, (round?.highestBidCents ?? 0) + 1);
              setAmount(String(safe + 1));
            }}
            disabled={loading || !isMyTurn}
            style={{ marginRight: 8 }}
          >
            +1
          </button>
          <button
            onClick={() => {
              const current = Number(amount);
              const safe = Number.isFinite(current) ? current : Math.max(1, (round?.highestBidCents ?? 0) + 1);
              setAmount(String(safe + 5));
            }}
            disabled={loading || !isMyTurn}
            style={{ marginRight: 8 }}
          >
            +5
          </button>
          <button onClick={handleBid} disabled={loading || !isMyTurn}>
            Place Bid
          </button>
          <p style={{ marginTop: 8, marginBottom: 0, fontSize: 13 }}>
            {isMyTurn ? "Your turn to bid." : "Waiting for opponent turn."}
          </p>
          <p style={{ marginTop: 8, marginBottom: 0, fontSize: 13 }}>
            Rule: You can bid repeatedly, but after any winning bid you must still keep
            at least 1 cent per remaining empty squad slot.
          </p>
        </div>
      )}

      {error && <p style={{ color: "red" }}>{error}</p>}
      {!isConnected && <p style={{ color: "#a15c00" }}>Reconnecting live updates...</p>}

      <div style={{ marginTop: 20, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <div style={{ background: "#f5f5f5", padding: 10 }}>
          <h4 style={{ marginTop: 0 }}>My Team ({myTeam.length})</h4>
          {myTeam.length === 0 && <p style={{ margin: 0 }}>No players won yet.</p>}
          {myTeam.map((card) => (
            <p key={card.roundId} style={{ margin: "4px 0" }}>
              {card.cardName} ({card.position}) - {card.amountCents}
            </p>
          ))}
        </div>
        <div style={{ background: "#f5f5f5", padding: 10 }}>
          <h4 style={{ marginTop: 0 }}>Opponent Team ({opponentTeam.length})</h4>
          {opponentTeam.length === 0 && <p style={{ margin: 0 }}>No players won yet.</p>}
          {opponentTeam.map((card) => (
            <p key={card.roundId} style={{ margin: "4px 0" }}>
              {card.cardName} ({card.position}) - {card.amountCents}
            </p>
          ))}
        </div>
      </div>

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
