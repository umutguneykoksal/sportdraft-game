import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  ApiError,
  AuctionRoundResponse,
  confirmMatchStart,
  getAuctionState,
  getMatchResult,
  getSessionSnapshot,
  passRound,
  placeBid,
  readyNextRound,
  SessionSnapshotResponse,
  startRound,
} from "../../shared/api";
import { clearActiveSessionId, setActiveSessionId } from "../../shared/session";
import { useSessionStream } from "../../shared/sse";
import { AuctionView } from "./components/AuctionView";

interface WonCard {
  cardId: string;
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
  const [sessionSnap, setSessionSnap] = useState<SessionSnapshotResponse | null>(null);
  const [matchStartSecondsLeft, setMatchStartSecondsLeft] = useState<number>(0);
  const previousRoundIdRef = useRef<string | null>(null);
  const serverClockOffsetMsRef = useRef<number>(0);
  const syncSeqRef = useRef(0);
  const isMyTurn = !!myUserId && !!round && round.currentTurnUserId === myUserId;
  const isReadyByMe = !!myUserId && !!round && (round.readyUserOneId === myUserId || round.readyUserTwoId === myUserId);
  const readyCount = round ? [round.readyUserOneId, round.readyUserTwoId].filter(Boolean).length : 0;
  const hasBidOnRound = !!round?.highestBidderId;
  const mySquadFull = myTeam.length >= 11;
  const auctionPhaseActive = sessionSnap?.status === "AUCTIONING";

  useEffect(() => {
    if (!sessionId) {
      return;
    }
    setActiveSessionId(sessionId);
    let cancelled = false;
    const syncSnapshot = async () => {
      const seq = ++syncSeqRef.current;
      try {
        const snapshot = await getSessionSnapshot(sessionId);
        if (cancelled || seq !== syncSeqRef.current) {
          return;
        }
        setSessionSnap(snapshot);
        if (snapshot.status === "FINISHED") {
          navigate(`/match/${sessionId}`);
          return;
        }
        const onAuctionPage =
          snapshot.status === "AUCTIONING"
          || snapshot.status === "MATCH_START_PENDING"
          || snapshot.status === "FINALIZING"
          || snapshot.status === "AWAITING_RESULT";
        if (!onAuctionPage) {
          navigate("/lobby");
          return;
        }
        const auctionState = await getAuctionState(sessionId);
        if (cancelled || seq !== syncSeqRef.current) {
          return;
        }
        setRound(auctionState.currentRound);
        setMyTeam(
          auctionState.myTeam.map((card) => ({
            cardId: card.cardId,
            cardName: card.cardName,
            position: card.position,
            amountCents: card.amountCents,
          }))
        );
        setOpponentTeam(
          auctionState.opponentTeam.map((card) => ({
            cardId: card.cardId,
            cardName: card.cardName,
            position: card.position,
            amountCents: card.amountCents,
          }))
        );
      } catch (e) {
        if (!cancelled && e instanceof ApiError && (e.status === 401 || e.status === 404)) {
          clearActiveSessionId();
          navigate("/lobby");
          return;
        }
        if (!cancelled) {
          setError("Connection issue while loading auction state. Retrying...");
        }
      }
    };
    syncSnapshot();
    return () => {
      cancelled = true;
    };
  }, [sessionId, navigate]);

  useEffect(() => {
    if (!sessionId || sessionSnap?.status !== "MATCH_START_PENDING") {
      return;
    }
    let cancelled = false;
    const id = window.setInterval(async () => {
      try {
        const snap = await getSessionSnapshot(sessionId);
        if (!cancelled) {
          setSessionSnap(snap);
        }
      } catch {
        // ignore
      }
    }, 1000);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, [sessionId, sessionSnap?.status]);

  /**
   * Rakip güncellemesi: bid / round değişince sunucu SSE ile `bid_placed`, `round_ended` vb. gönderir (tek uzun GET).
   * Bu interval sadece “yedek senkron”: SSE koptuysa daha sık, sağlamsa seyrek REST (sunucu yükü düşük).
   */
  useEffect(() => {
    if (!sessionId) {
      return;
    }
    if (sessionSnap && sessionSnap.status !== "AUCTIONING") {
      return;
    }
    let cancelled = false;
    const pull = async () => {
      const seq = ++syncSeqRef.current;
      try {
        const [snapshot, auctionState] = await Promise.all([
          getSessionSnapshot(sessionId),
          getAuctionState(sessionId),
        ]);
        if (cancelled || seq !== syncSeqRef.current) {
          return;
        }
        setSessionSnap(snapshot);
        if (snapshot.status === "FINISHED") {
          navigate(`/match/${sessionId}`);
          return;
        }
        const stayHere =
          snapshot.status === "AUCTIONING"
          || snapshot.status === "MATCH_START_PENDING"
          || snapshot.status === "FINALIZING"
          || snapshot.status === "AWAITING_RESULT";
        if (!stayHere) {
          return;
        }
        setRound(auctionState.currentRound);
        setMyTeam(
          auctionState.myTeam.map((card) => ({
            cardId: card.cardId,
            cardName: card.cardName,
            position: card.position,
            amountCents: card.amountCents,
          }))
        );
        setOpponentTeam(
          auctionState.opponentTeam.map((card) => ({
            cardId: card.cardId,
            cardName: card.cardName,
            position: card.position,
            amountCents: card.amountCents,
          }))
        );
      } catch {
        // transient network — keep last known UI
      }
    };
    pull();
    const intervalMs = isConnected ? 5000 : 2000;
    const id = window.setInterval(pull, intervalMs);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, [sessionId, sessionSnap?.status, navigate, isConnected]);

  useEffect(() => {
    if (!sessionId) {
      return;
    }
    const simulating =
      sessionSnap?.status === "AWAITING_RESULT"
      || sessionSnap?.status === "FINALIZING";
    if (!simulating) {
      return;
    }
    let cancelled = false;
    const tick = async () => {
      try {
        const snap = await getSessionSnapshot(sessionId);
        if (cancelled) {
          return;
        }
        setSessionSnap(snap);
        if (snap.status === "FINISHED") {
          navigate(`/match/${sessionId}`);
          return;
        }
        const result = await getMatchResult(sessionId);
        if (!cancelled && result !== null) {
          navigate(`/match/${sessionId}`);
        }
      } catch {
        // ignore transient errors while polling
      }
    };
    tick();
    const id = window.setInterval(tick, 1000);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, [sessionId, sessionSnap?.status, navigate]);

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
        const onAuctionPage =
          snapshot.status === "AUCTIONING"
          || snapshot.status === "MATCH_START_PENDING"
          || snapshot.status === "FINALIZING"
          || snapshot.status === "AWAITING_RESULT";
        if (!onAuctionPage) {
          navigate("/lobby");
          return;
        }
        setSessionSnap(snapshot);
        if (snapshot.status === "AUCTIONING") {
          const auctionState = await getAuctionState(sessionId);
          setRound(auctionState.currentRound);
          setMyTeam(
            auctionState.myTeam.map((card) => ({
              cardId: card.cardId,
              cardName: card.cardName,
              position: card.position,
              amountCents: card.amountCents,
            }))
          );
          setOpponentTeam(
            auctionState.opponentTeam.map((card) => ({
              cardId: card.cardId,
              cardName: card.cardName,
              position: card.position,
              amountCents: card.amountCents,
            }))
          );
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
    if (
      (latest.eventType === "round_started"
        || latest.eventType === "bid_placed"
        || latest.eventType === "round_ended"
        || latest.eventType === "round_ready")
      && payload.round
    ) {
      const serverEventTime = Date.parse(latest.occurredAt);
      if (!Number.isNaN(serverEventTime)) {
        serverClockOffsetMsRef.current = serverEventTime - Date.now();
      }
      setRound(payload.round);
    }
    if (latest.eventType === "round_ended" && sessionId) {
      (async () => {
        const seq = ++syncSeqRef.current;
        try {
          const snapshot = await getSessionSnapshot(sessionId);
          if (seq !== syncSeqRef.current) {
            return;
          }
          setSessionSnap(snapshot);
          if (snapshot.status === "AUCTIONING") {
            const auctionState = await getAuctionState(sessionId);
            if (seq !== syncSeqRef.current) {
              return;
            }
            setRound(auctionState.currentRound);
            setMyTeam(
              auctionState.myTeam.map((card) => ({
                cardId: card.cardId,
                cardName: card.cardName,
                position: card.position,
                amountCents: card.amountCents,
              }))
            );
            setOpponentTeam(
              auctionState.opponentTeam.map((card) => ({
                cardId: card.cardId,
                cardName: card.cardName,
                position: card.position,
                amountCents: card.amountCents,
              }))
            );
          }
        } catch {
          // ignore
        }
      })();
    }
    if (
      (latest.eventType === "match_start_pending"
        || latest.eventType === "match_start_ready"
        || latest.eventType === "session_finalizing")
      && sessionId
    ) {
      const serverEventTime = Date.parse(latest.occurredAt);
      if (!Number.isNaN(serverEventTime)) {
        serverClockOffsetMsRef.current = serverEventTime - Date.now();
      }
      getSessionSnapshot(sessionId).then(setSessionSnap).catch(() => {});
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
    const roundChanged = previousRoundIdRef.current !== round.roundId;
    if (roundChanged) {
      previousRoundIdRef.current = round.roundId;
      setAmount(String(minimum));
      return;
    }
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
      const adjustedNow = Date.now() + serverClockOffsetMsRef.current;
      const diffMs = new Date(round.endsAt).getTime() - adjustedNow;
      return Math.max(0, Math.ceil(diffMs / 1000));
    };
    setSecondsLeft(computeSecondsLeft());
    const timer = setInterval(() => {
      setSecondsLeft(computeSecondsLeft());
    }, 250);
    return () => clearInterval(timer);
  }, [round]);

  useEffect(() => {
    if (!sessionSnap?.matchStartDeadlineAt) {
      setMatchStartSecondsLeft(0);
      return;
    }
    const deadlineMs = new Date(sessionSnap.matchStartDeadlineAt).getTime();
    const tick = () => {
      const adjustedNow = Date.now() + serverClockOffsetMsRef.current;
      return Math.max(0, Math.ceil((deadlineMs - adjustedNow) / 1000));
    };
    setMatchStartSecondsLeft(tick());
    const timer = window.setInterval(() => {
      setMatchStartSecondsLeft(tick());
    }, 250);
    return () => window.clearInterval(timer);
  }, [sessionSnap?.matchStartDeadlineAt]);

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
    if (myTeam.length >= 11) {
      setError("Your squad is full");
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

  const handleReadyNext = async () => {
    if (!round) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await readyNextRound(sessionId, round.roundId);
      setRound(response);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to ready next");
    } finally {
      setLoading(false);
    }
  };

  const handlePassRound = async () => {
    if (!round) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const response = await passRound(sessionId, round.roundId);
      setRound(response);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to pass round");
    } finally {
      setLoading(false);
    }
  };

  const handleConfirmMatchStart = async () => {
    if (!sessionId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const snap = await confirmMatchStart(sessionId);
      setSessionSnap(snap);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to confirm match start");
    } finally {
      setLoading(false);
    }
  };

  return (
    <AuctionView title="Auction">
      <p>Session: {sessionId}</p>

      {sessionSnap?.status === "MATCH_START_PENDING" && (
        <div
          style={{
            marginTop: 12,
            marginBottom: 16,
            padding: 16,
            background: "#e8f4fc",
            border: "1px solid #7eb8da",
            borderRadius: 8,
          }}
        >
          <h3 style={{ marginTop: 0 }}>Maç başlatma</h3>
          <p style={{ margin: "8px 0" }}>
            Her iki takım da 11 oyuncuya ulaştı. Aşağıdaki süre dolmadan ikiniz de &quot;Maçı başlat&quot; demelisiniz; aksi halde süre bitince maç yine de başlar.
          </p>
          <p style={{ margin: "8px 0", fontSize: 18, fontWeight: "bold" }}>
            Kalan süre: {matchStartSecondsLeft}s
          </p>
          <p style={{ margin: "8px 0", fontSize: 14 }}>
            Onaylar: {sessionSnap.matchStartReadyCount}/2
          </p>
          <button
            type="button"
            onClick={handleConfirmMatchStart}
            disabled={loading || sessionSnap.matchStartConfirmedByMe}
            style={{ marginTop: 8 }}
          >
            {sessionSnap.matchStartConfirmedByMe ? "Hazırsınız" : "Maçı başlat"}
          </button>
        </div>
      )}

      {(sessionSnap?.status === "AWAITING_RESULT" || sessionSnap?.status === "FINALIZING") && (
        <p style={{ color: "#333" }}>
          Maç simüle ediliyor… Birkaç saniye içinde sonuç sayfasına geçmeli; olmazsa sayfayı yenileyin.
        </p>
      )}

      <button onClick={handleStartRound} disabled={loading || !auctionPhaseActive}>
        {loading ? "Working..." : "Start Round"}
      </button>

      {round && auctionPhaseActive && (
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
            disabled={loading || !isMyTurn || mySquadFull}
            style={{ marginRight: 8 }}
          />
          <button
            onClick={() => {
              const current = Number(amount);
              const safe = Number.isFinite(current) ? current : Math.max(1, (round?.highestBidCents ?? 0) + 1);
              setAmount(String(safe + 1));
            }}
            disabled={loading || !isMyTurn || mySquadFull}
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
            disabled={loading || !isMyTurn || mySquadFull}
            style={{ marginRight: 8 }}
          >
            +5
          </button>
          <button onClick={handleBid} disabled={loading || !isMyTurn || mySquadFull}>
            Place Bid
          </button>
          <button
            onClick={handlePassRound}
            disabled={loading || !isMyTurn}
            style={{ marginLeft: 8 }}
          >
            Pass (End Round)
          </button>
          <button
            onClick={handleReadyNext}
            disabled={loading || round.status !== "OPEN" || isReadyByMe || hasBidOnRound}
            style={{ marginLeft: 8 }}
          >
            {isReadyByMe ? "Ready Sent" : "Ready Next"}
          </button>
          <p style={{ marginTop: 8, marginBottom: 0, fontSize: 13 }}>
            Ready votes: {readyCount}/2
          </p>
          <p style={{ marginTop: 8, marginBottom: 0, fontSize: 13 }}>
            {mySquadFull
              ? "Your squad is full (11/11) — you cannot bid on new cards."
              : isMyTurn
                ? "Your turn to bid."
                : "Waiting for opponent turn."}
          </p>
          <p style={{ marginTop: 8, marginBottom: 0, fontSize: 13 }}>
            Rule: You can bid repeatedly, but after any winning bid you must still keep
            at least 1 cent per remaining empty squad slot.
          </p>
        </div>
      )}

      {auctionPhaseActive && !round && (
        <p style={{ marginTop: 12, color: "#555" }}>No open round — start one when both are ready.</p>
      )}

      {error && <p style={{ color: "red" }}>{error}</p>}
      {!isConnected && <p style={{ color: "#a15c00" }}>Reconnecting live updates...</p>}

      <div style={{ marginTop: 20, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        <div style={{ background: "#f5f5f5", padding: 10 }}>
          <h4 style={{ marginTop: 0 }}>My Team ({myTeam.length}/11)</h4>
          {myTeam.length === 0 && <p style={{ margin: 0 }}>No players won yet.</p>}
          {myTeam.map((card) => (
            <p key={card.cardId} style={{ margin: "4px 0" }}>
              {card.cardName} ({card.position}) - {card.amountCents}
            </p>
          ))}
        </div>
        <div style={{ background: "#f5f5f5", padding: 10 }}>
          <h4 style={{ marginTop: 0 }}>Opponent Team ({opponentTeam.length}/11)</h4>
          {opponentTeam.length === 0 && <p style={{ margin: 0 }}>No players won yet.</p>}
          {opponentTeam.map((card) => (
            <p key={card.cardId} style={{ margin: "4px 0" }}>
              {card.cardName} ({card.position}) - {card.amountCents}
            </p>
          ))}
        </div>
      </div>
    </AuctionView>
  );
}
