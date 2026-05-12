import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  ApiError,
  AuctionRoundResponse,
  AuctionStateResponse,
  AuctionTeamCardResponse,
  confirmMatchStart,
  getAuctionState,
  getSessionSnapshot,
  passRound,
  placeBid,
  SessionSnapshotResponse,
  startRound,
} from "../../shared/api";
import { ROUND_STATUS_LABELS } from "../../shared/api/contracts";
import { clearActiveSessionId, setActiveSessionId } from "../../shared/session";
import { useSessionStream } from "../../shared/sse";
import { TeamPitch } from "../../shared/components/TeamPitch";
import { FORMATION_LABELS } from "../../shared/formations";
import type { Formation, Position } from "../../shared/api/contracts";

function mapCards(raw: AuctionTeamCardResponse[]): AuctionTeamCardResponse[] {
  return raw.map((c) => ({
    cardId: c.cardId,
    cardName: c.cardName,
    position: c.position,
    rating: c.rating ?? 0,
    amountCents: c.amountCents,
  }));
}

export function AuctionPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const myUserId = localStorage.getItem("sessionToken");

  const [round, setRound] = useState<AuctionRoundResponse | null>(null);
  const [amount, setAmount] = useState<string>("1");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [myTeam, setMyTeam] = useState<AuctionTeamCardResponse[]>([]);
  const [opponentTeam, setOpponentTeam] = useState<AuctionTeamCardResponse[]>([]);
  const [myFormation, setMyFormation] = useState<Formation>("FOUR_FOUR_TWO");
  const [opponentFormation, setOpponentFormation] = useState<Formation>("FOUR_FOUR_TWO");
  const [secondsLeft, setSecondsLeft] = useState<number>(0);
  const [sessionSnap, setSessionSnap] = useState<SessionSnapshotResponse | null>(null);
  const [matchStartSecondsLeft, setMatchStartSecondsLeft] = useState<number>(0);
  const previousRoundIdRef = useRef<string | null>(null);
  const serverClockOffsetMsRef = useRef<number>(0);
  const roundTotalSecondsRef = useRef<number>(15);
  const syncSeqRef = useRef(0);
  const prevConnectedRef = useRef(false);
  const isMyTurn = !!myUserId && !!round && round.currentTurnUserId === myUserId;
  const mySquadFull = myTeam.length >= 11;
  const auctionPhaseActive = sessionSnap?.status === "AUCTIONING";
  const streamSessionId = sessionId && sessionSnap?.joined ? sessionId : null;
  const { events, isConnected } = useSessionStream(streamSessionId);


  // Budget: starting 100 minus spent
  const spent = myTeam.reduce((sum, c) => sum + c.amountCents, 0);
  const budget = 100 - spent;

  // ---- Shared helpers ----

  const applyAuctionState = (
    snapshot: SessionSnapshotResponse,
    auctionState: AuctionStateResponse,
  ) => {
    setSessionSnap(snapshot);
    setRound(auctionState.currentRound);
    setMyTeam(mapCards(auctionState.myTeam));
    setOpponentTeam(mapCards(auctionState.opponentTeam));
    if (auctionState.myFormation) setMyFormation(auctionState.myFormation as Formation);
    if (auctionState.opponentFormation) setOpponentFormation(auctionState.opponentFormation);
  };

  const isActivePage = (status: string) =>
    status === "AUCTIONING" || status === "MATCH_START_PENDING"
    || status === "FINALIZING" || status === "AWAITING_RESULT";

  // ---- Effects ----

  // 1. Initial load (runs once on mount)
  useEffect(() => {
    if (!sessionId) return;
    setActiveSessionId(sessionId);
    let cancelled = false;
    (async () => {
      const seq = ++syncSeqRef.current;
      try {
        const snapshot = await getSessionSnapshot(sessionId);
        if (cancelled || seq !== syncSeqRef.current) return;
        if (snapshot.status === "FINISHED") { navigate(`/match/${sessionId}`); return; }
        if (!isActivePage(snapshot.status)) { navigate("/lobby"); return; }
        const auctionState = await getAuctionState(sessionId);
        if (cancelled || seq !== syncSeqRef.current) return;
        applyAuctionState(snapshot, auctionState);
      } catch (e) {
        if (!cancelled && e instanceof ApiError && (e.status === 401 || e.status === 404)) {
          clearActiveSessionId(); navigate("/lobby"); return;
        }
        if (!cancelled) setError("Connection issue while loading auction state. Retrying...");
      }
    })();
    return () => { cancelled = true; };
  }, [sessionId, navigate]);

  // 2. Safety-net poll (SSE connected → 30s, disconnected → 3s; initial load handled above)
  useEffect(() => {
    if (!sessionId) return;
    if (isConnected && sessionSnap && sessionSnap.status !== "AUCTIONING") return;
    let cancelled = false;
    const poll = async () => {
      const seq = ++syncSeqRef.current;
      try {
        const [snapshot, auctionState] = await Promise.all([
          getSessionSnapshot(sessionId),
          getAuctionState(sessionId),
        ]);
        if (cancelled || seq !== syncSeqRef.current) return;
        if (snapshot.status === "FINISHED") { navigate(`/match/${sessionId}`); return; }
        if (!isActivePage(snapshot.status)) return;
        applyAuctionState(snapshot, auctionState);
      } catch { /* transient */ }
    };
    const intervalMs = isConnected ? 30000 : 3000;
    const id = window.setInterval(poll, intervalMs);
    return () => { cancelled = true; window.clearInterval(id); };
  }, [sessionId, sessionSnap?.status, navigate, isConnected]);

  // 3. SSE reconnection catch-up (skips the initial connect, only fires on re-connect)
  useEffect(() => {
    const wasConnected = prevConnectedRef.current;
    prevConnectedRef.current = isConnected;
    if (!sessionId || !isConnected || !wasConnected) return;
    (async () => {
      const seq = ++syncSeqRef.current;
      try {
        const snapshot = await getSessionSnapshot(sessionId);
        if (seq !== syncSeqRef.current) return;
        if (snapshot.status === "FINISHED") { navigate(`/match/${sessionId}`); return; }
        if (!isActivePage(snapshot.status)) { navigate("/lobby"); return; }
        setSessionSnap(snapshot);
        if (snapshot.status === "AUCTIONING") {
          const auctionState = await getAuctionState(sessionId);
          if (seq !== syncSeqRef.current) return;
          applyAuctionState(snapshot, auctionState);
        }
      } catch { /* ignore */ }
    })();
  }, [isConnected, navigate, sessionId]);

  // 4. SSE event handler (real-time state updates driven by server push)
  useEffect(() => {
    const latest = events.at(-1);
    if (!latest) return;
    const payload = latest.payload as {
      round?: AuctionRoundResponse;
      sessionId?: string;
      result?: string;
      winnerUserId?: string;
    };

    // Auction round events → update round from payload
    if (
      (latest.eventType === "round_started" || latest.eventType === "bid_placed"
        || latest.eventType === "round_ended" || latest.eventType === "round_ready"
        || latest.eventType === "turn_passed")
      && payload.round
    ) {
      const serverEventTime = Date.parse(latest.occurredAt);
      if (!Number.isNaN(serverEventTime)) serverClockOffsetMsRef.current = serverEventTime - Date.now();
      setRound(payload.round);
    }

    // Round ended → refresh full state (teams, next round)
    if (latest.eventType === "round_ended" && sessionId) {
      (async () => {
        const seq = ++syncSeqRef.current;
        try {
          const snapshot = await getSessionSnapshot(sessionId);
          if (seq !== syncSeqRef.current) return;
          setSessionSnap(snapshot);
          if (snapshot.status === "AUCTIONING") {
            const auctionState = await getAuctionState(sessionId);
            if (seq !== syncSeqRef.current) return;
            applyAuctionState(snapshot, auctionState);
          }
        } catch { /* ignore */ }
      })();
    }

    // Session phase transitions → refresh snapshot + auction state
    if (
      (latest.eventType === "match_start_pending" || latest.eventType === "match_start_ready"
        || latest.eventType === "session_finalizing") && sessionId
    ) {
      const serverEventTime = Date.parse(latest.occurredAt);
      if (!Number.isNaN(serverEventTime)) serverClockOffsetMsRef.current = serverEventTime - Date.now();
      (async () => {
        try {
          const [snap, auctionState] = await Promise.all([
            getSessionSnapshot(sessionId),
            getAuctionState(sessionId),
          ]);
          applyAuctionState(snap, auctionState);
        } catch { /* ignore */ }
      })();
    }

    // Match completed → navigate to result page
    if (latest.eventType === "match_completed" && sessionId) navigate(`/match/${sessionId}`);
  }, [events, myUserId, navigate, sessionId]);

  // 5. Bid amount auto-adjust on round change
  useEffect(() => {
    if (!round) return;
    const minimum = Math.max(1, round.highestBidCents + 1);
    const roundChanged = previousRoundIdRef.current !== round.roundId;
    if (roundChanged) { previousRoundIdRef.current = round.roundId; setAmount(String(minimum)); return; }
    setAmount((prev) => {
      const parsed = Number(prev);
      if (!Number.isFinite(parsed) || parsed < minimum) return String(minimum);
      return prev;
    });
  }, [round]);

  // 6. Round countdown timer
  useEffect(() => {
    if (!round) { setSecondsLeft(0); return; }
    const computeSecondsLeft = () => {
      const adjustedNow = Date.now() + serverClockOffsetMsRef.current;
      return Math.max(0, Math.ceil((new Date(round.endsAt).getTime() - adjustedNow) / 1000));
    };
    const initial = computeSecondsLeft();
    if (previousRoundIdRef.current !== round.roundId) {
      roundTotalSecondsRef.current = initial;
    } else if (initial > roundTotalSecondsRef.current) {
      roundTotalSecondsRef.current = initial;
    }
    setSecondsLeft(initial);
    const timer = setInterval(() => setSecondsLeft(computeSecondsLeft()), 250);
    return () => clearInterval(timer);
  }, [round]);

  // 7. Match-start deadline countdown timer
  useEffect(() => {
    if (!sessionSnap?.matchStartDeadlineAt) { setMatchStartSecondsLeft(0); return; }
    const deadlineMs = new Date(sessionSnap.matchStartDeadlineAt).getTime();
    const tick = () => {
      const adjustedNow = Date.now() + serverClockOffsetMsRef.current;
      return Math.max(0, Math.ceil((deadlineMs - adjustedNow) / 1000));
    };
    setMatchStartSecondsLeft(tick());
    const timer = window.setInterval(() => setMatchStartSecondsLeft(tick()), 250);
    return () => window.clearInterval(timer);
  }, [sessionSnap?.matchStartDeadlineAt]);

  // 8. Auto-start next round when auction is active but no round exists
  const autoStartRef = useRef(false);
  useEffect(() => {
    if (!sessionId || !auctionPhaseActive || round || loading || autoStartRef.current) return;
    autoStartRef.current = true;
    (async () => {
      try {
        setRound(await startRound(sessionId));
      } catch {
        // round may have already been started by the other player; poll will catch up
      } finally {
        autoStartRef.current = false;
      }
    })();
  }, [sessionId, auctionPhaseActive, round, loading]);

  // ---- Handlers ----

  if (!sessionId) return <div className="p-8 text-white">Session ID missing</div>;

  const handleTake = async () => {
    if (!round) return;
    setLoading(true); setError(null);
    try { setRound(await placeBid(sessionId, round.roundId, 1)); }
    catch (e) { setError(e instanceof Error ? e.message : "Failed to take card"); }
    finally { setLoading(false); }
  };

  const handleBid = async () => {
    if (!round) { setError("Start a round first"); return; }
    if (myTeam.length >= 11) { setError("Your squad is full"); return; }
    if (!isMyTurn) { setError("It is not your turn"); return; }
    const amountCents = Number(amount);
    if (!Number.isFinite(amountCents) || amountCents <= 0) { setError("Amount must be a positive number"); return; }
    if (amountCents > budget) { setError(`Insufficient budget. Maximum bid: ${budget} cr`); return; }
    setLoading(true); setError(null);
    try { setRound(await placeBid(sessionId, round.roundId, amountCents)); }
    catch (e) { setError(e instanceof Error ? e.message : "Failed to place bid"); }
    finally { setLoading(false); }
  };


  const handlePassRound = async () => {
    if (!round) return;
    setLoading(true); setError(null);
    try { setRound(await passRound(sessionId, round.roundId)); }
    catch (e) { setError(e instanceof Error ? e.message : "Failed to pass round"); }
    finally { setLoading(false); }
  };

  const handleConfirmMatchStart = async () => {
    if (!sessionId) return;
    setLoading(true); setError(null);
    try { setSessionSnap(await confirmMatchStart(sessionId)); }
    catch (e) { setError(e instanceof Error ? e.message : "Failed to confirm match start"); }
    finally { setLoading(false); }
  };

  // ---- Computed ----

  const timerPercent = round && roundTotalSecondsRef.current > 0
    ? Math.min(100, (secondsLeft / roundTotalSecondsRef.current) * 100)
    : 0;
  const timerColor = secondsLeft <= 5 ? "bg-red-500" : secondsLeft <= 10 ? "bg-amber-500" : "bg-accent";
  const budgetPercent = Math.max(0, (budget / 100) * 100);

  const positionColors: Record<string, string> = {
    GK: "bg-yellow-500",
    DEF: "bg-blue-500",
    CDM: "bg-indigo-500",
    CM: "bg-green-500",
    CAM: "bg-orange-500",
    FWD: "bg-red-500",
  };

  // ---- Render ----

  return (
    <div className="min-h-screen bg-surface">
      {/* Top Bar */}
      <div className="bg-surface-card border-b border-white/10 px-4 py-3">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2 sm:gap-4">
            <h1 className="text-lg sm:text-xl font-bold text-white">Auction Draft</h1>
            <span className="text-xs bg-surface-elevated text-slate-400 px-2 py-1 rounded">
              {FORMATION_LABELS[myFormation]}
            </span>
          </div>
          <div className="flex items-center gap-3">
            <div className="text-right">
              <p className="text-xs text-slate-400">Budget</p>
              <p className="text-base sm:text-lg font-bold text-white">{budget} <span className="text-xs text-slate-400">cr</span></p>
            </div>
            <div className="w-20 sm:w-32 h-2 bg-surface-elevated rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-300 ${budgetPercent > 30 ? "bg-accent-green" : budgetPercent > 10 ? "bg-amber-500" : "bg-red-500"}`}
                style={{ width: `${budgetPercent}%` }}
              />
            </div>
            {!isConnected && (
              <span className="text-amber-400 text-xs animate-pulse">Reconnecting...</span>
            )}
          </div>
        </div>
      </div>

      {/* Squads Complete */}
      {sessionSnap?.status === "MATCH_START_PENDING" && (
        <div className="bg-accent-green/10 border-b border-accent-green/30 px-4 py-4 text-center">
          <p className="text-lg font-bold text-accent-green animate-pulse">All squads complete! Starting match...</p>
        </div>
      )}

      {/* Simulating */}
      {(sessionSnap?.status === "AWAITING_RESULT" || sessionSnap?.status === "FINALIZING") && (
        <div className="bg-surface-card border-b border-white/10 px-4 py-6 text-center">
          <div className="animate-pulse">
            <p className="text-xl font-bold text-white">Simulating Match...</p>
            <p className="text-slate-400 text-sm mt-1">Results coming shortly</p>
          </div>
        </div>
      )}

      {/* Main 3-Column Layout */}
      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px_1fr] gap-6">

          {/* LEFT: My Team Pitch */}
          <div className="bg-surface-card rounded-xl border border-white/10 overflow-hidden">
            <div className="px-4 py-3 border-b border-white/10 flex items-center justify-between">
              <h3 className="font-bold text-white">My Team</h3>
              <span className="text-sm text-slate-400">{myTeam.length}/11</span>
            </div>
            <div className="p-2">
              <TeamPitch
                formation={myFormation}
                cards={myTeam}
                teamColor="#3b82f6"
                teamBorderColor="#60a5fa"
                highlightPosition={round?.position as Position | undefined}
              />
            </div>
            {/* Card List */}
            <div className="px-4 py-2 border-t border-white/10 max-h-40 overflow-y-auto">
              {myTeam.length === 0 ? (
                <p className="text-slate-500 text-sm text-center py-2">No players yet</p>
              ) : (
                <div className="space-y-1">
                  {myTeam.map((c) => (
                    <div key={c.cardId} className="flex items-center justify-between text-sm">
                      <div className="flex items-center gap-2">
                        <span className={`${positionColors[c.position] ?? "bg-gray-500"} text-white text-xs px-1.5 py-0.5 rounded font-bold`}>
                          {c.position}
                        </span>
                        <span className="text-white">{c.cardName}</span>
                        <span className="text-slate-500">{c.rating}</span>
                      </div>
                      <span className="text-slate-400">{c.amountCents} cr</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* CENTER: Auction Panel */}
          <div className="space-y-4">
            {/* Current Card */}
            {round && auctionPhaseActive && (
              <div className="bg-surface-card rounded-xl border border-white/10 p-4">
                <div className="text-center mb-4">
                  <span className={`${positionColors[round.position] ?? "bg-gray-500"} text-white text-xs px-2 py-1 rounded-full font-bold`}>
                    {round.position}
                  </span>
                  <h2 className="text-2xl font-extrabold text-white mt-2">{round.cardName}</h2>
                  <p className="text-4xl font-black text-card-gold mt-1">{round.cardRating ?? "?"}</p>
                  <p className="text-xs text-slate-400 mt-1">Rating</p>
                </div>

                {/* Timer Bar */}
                <div className="mb-4">
                  <div className="flex justify-between text-xs text-slate-400 mb-1">
                    <span>Time Left</span>
                    <span className={`font-bold ${secondsLeft <= 5 ? "text-red-400" : "text-white"}`}>
                      {secondsLeft}s
                    </span>
                  </div>
                  <div className="w-full h-2 bg-surface-elevated rounded-full overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all duration-300 ${timerColor}`}
                      style={{ width: `${timerPercent}%` }}
                    />
                  </div>
                </div>

                {/* Current Bid */}
                <div className="bg-surface-elevated rounded-lg p-3 mb-4 text-center">
                  <p className="text-xs text-slate-400">Highest Bid</p>
                  <p className="text-2xl font-bold text-white">
                    {round.highestBidCents > 0
                      ? `${round.highestBidCents} cr`
                      : "No bids"}
                  </p>
                  <p className="text-xs text-slate-400 mt-1">
                    {ROUND_STATUS_LABELS[round.status] ?? round.status}
                  </p>
                </div>

                {/* Bid Controls */}
                {isMyTurn && round.opponentCanBid === false ? (
                  <div className="space-y-3">
                    <div className="bg-amber-900/20 border border-amber-500/30 rounded-lg px-3 py-2 text-center">
                      <p className="text-amber-400 text-sm font-medium">Opponent can't bid on this card</p>
                    </div>
                    <button
                      onClick={handleTake}
                      disabled={loading || mySquadFull}
                      className="w-full bg-accent-green hover:bg-green-600 disabled:opacity-40 text-white font-bold py-3 rounded-lg transition-colors text-lg"
                    >
                      Take for 1 cr
                    </button>
                    <button
                      onClick={handlePassRound}
                      disabled={loading}
                      className="w-full bg-red-900/30 border border-red-500/30 text-red-400 hover:bg-red-900/50 disabled:opacity-40 py-2 rounded-lg transition-colors text-sm font-medium"
                    >
                      Pass
                    </button>
                  </div>
                ) : (
                  <div className="space-y-3">
                    <div className="flex gap-2">
                      <input
                        value={amount}
                        onChange={(e) => setAmount(e.target.value)}
                        type="number"
                        min={1}
                        max={budget}
                        placeholder="Bid (cr)"
                        disabled={loading || !isMyTurn || mySquadFull}
                        className="flex-1 bg-surface-elevated border border-white/10 rounded-lg px-3 py-2 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-accent disabled:opacity-40"
                      />
                      <button
                        onClick={() => {
                          const current = Number(amount);
                          const safe = Number.isFinite(current) ? current : Math.max(1, (round?.highestBidCents ?? 0) + 1);
                          setAmount(String(Math.min(safe + 1, budget)));
                        }}
                        disabled={loading || !isMyTurn || mySquadFull}
                        className="bg-surface-elevated border border-white/10 text-white px-3 py-2 rounded-lg hover:bg-white/10 disabled:opacity-40 transition-colors"
                      >
                        +1
                      </button>
                      <button
                        onClick={() => {
                          const current = Number(amount);
                          const safe = Number.isFinite(current) ? current : Math.max(1, (round?.highestBidCents ?? 0) + 1);
                          setAmount(String(Math.min(safe + 5, budget)));
                        }}
                        disabled={loading || !isMyTurn || mySquadFull}
                        className="bg-surface-elevated border border-white/10 text-white px-3 py-2 rounded-lg hover:bg-white/10 disabled:opacity-40 transition-colors"
                      >
                        +5
                      </button>
                    </div>

                    <button
                      onClick={handleBid}
                      disabled={loading || !isMyTurn || mySquadFull}
                      className="w-full bg-accent hover:bg-blue-600 disabled:opacity-40 text-white font-bold py-3 rounded-lg transition-colors"
                    >
                      Place Bid
                    </button>

                    <button
                      onClick={handlePassRound}
                      disabled={loading || !isMyTurn}
                      className="w-full bg-red-900/30 border border-red-500/30 text-red-400 hover:bg-red-900/50 disabled:opacity-40 py-2 rounded-lg transition-colors text-sm font-medium"
                    >
                      Pass
                    </button>
                  </div>
                )}

                {/* Turn Indicator */}
                <div className={`mt-3 text-center text-sm font-medium py-2 rounded-lg ${
                  mySquadFull
                    ? "bg-accent-green/10 text-accent-green"
                    : isMyTurn
                      ? "bg-accent/10 text-accent"
                      : "bg-surface-elevated text-slate-400"
                }`}>
                  {mySquadFull
                    ? "Squad full (11/11)"
                    : isMyTurn
                      ? "Your turn to bid!"
                      : "Waiting for opponent..."}
                </div>
              </div>
            )}

            {/* Waiting for next round */}
            {auctionPhaseActive && !round && (
              <div className="bg-surface-card rounded-xl border border-white/10 p-6 text-center">
                <p className="text-slate-400 animate-pulse">Starting next round...</p>
              </div>
            )}

            {/* Error */}
            {error && (
              <div className="bg-red-900/30 border border-red-500/30 text-red-400 px-4 py-2 rounded-lg text-sm">
                {error}
              </div>
            )}
          </div>

          {/* RIGHT: Opponent Team Pitch */}
          <div className="bg-surface-card rounded-xl border border-white/10 overflow-hidden">
            <div className="px-4 py-3 border-b border-white/10 flex items-center justify-between">
              <h3 className="font-bold text-white">Opponent</h3>
              <span className="text-sm text-slate-400">{opponentTeam.length}/11</span>
            </div>
            <div className="p-2">
              <TeamPitch
                formation={opponentFormation}
                cards={opponentTeam}
                away
                teamColor="#ef4444"
                teamBorderColor="#f87171"
              />
            </div>
            <div className="px-4 py-2 border-t border-white/10 max-h-40 overflow-y-auto">
              {opponentTeam.length === 0 ? (
                <p className="text-slate-500 text-sm text-center py-2">No players yet</p>
              ) : (
                <div className="space-y-1">
                  {opponentTeam.map((c) => (
                    <div key={c.cardId} className="flex items-center justify-between text-sm">
                      <div className="flex items-center gap-2">
                        <span className={`${positionColors[c.position] ?? "bg-gray-500"} text-white text-xs px-1.5 py-0.5 rounded font-bold`}>
                          {c.position}
                        </span>
                        <span className="text-white">{c.cardName}</span>
                        <span className="text-slate-500">{c.rating}</span>
                      </div>
                      <span className="text-slate-400">{c.amountCents} cr</span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

        </div>
      </div>
    </div>
  );
}
