import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  AuctionRoundResponse,
  AuctionTeamCardResponse,
  confirmMatchStart,
  getAuctionState,
  passRound,
  pingSession,
  placeBid,
} from "../../shared/api";
import { ROUND_STATUS_LABELS } from "../../shared/api/contracts";
import { clearActiveSessionId, setActiveSessionId } from "../../shared/session";
import { useSessionSync } from "../../shared/realtime/useSessionSync";
import { TeamPitch } from "../../shared/components/TeamPitch";
import { PositionBadgeRow } from "../../shared/components/PositionBadgeRow";
import { PlayerAttributeBars } from "../../shared/components/PlayerAttributeBars";
import { FORMATION_LABELS } from "../../shared/formations";
import { openEligibleHighlights } from "../../shared/positions";
import type { Formation } from "../../shared/api/contracts";
import { deriveViewerFlags } from "./deriveViewerFlags";

/** UI lock / transition delay after timer hits 0 (may be >= backend grace; not required to match). */
const BID_UI_GRACE_MS = 2000;

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

  const [amount, setAmount] = useState<string>("1");
  const [actionError, setActionError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [secondsLeft, setSecondsLeft] = useState<number>(0);
  const [matchStartSecondsLeft, setMatchStartSecondsLeft] = useState<number>(0);
  const [timeExtended, setTimeExtended] = useState(false);
  const [inGracePeriod, setInGracePeriod] = useState(false);
  const previousRoundIdRef = useRef<string | null>(null);
  const countdownRoundIdRef = useRef<string | null>(null);
  const previousSecondsLeftRef = useRef<number>(0);
  const graceTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const serverClockOffsetMsRef = useRef<number>(0);
  const roundTotalSecondsRef = useRef<number>(30);
  const [turnIsOpen, setTurnIsOpen] = useState(false);

  // Central sync: replaces effects 1–4 (initial load, poll, reconnect, event handler)
  const { snapshot: sessionSnap, auctionState, round, applyActorRound, isConnected, lastEvent, error: syncError, sessionNotFound, matchCompleted } =
    useSessionSync(sessionId ?? null);
  const [resolvingRoundId, setResolvingRoundId] = useState<string | null>(null);

  const myTeam = auctionState ? mapCards(auctionState.myTeam) : [];
  const opponentTeam = auctionState ? mapCards(auctionState.opponentTeam) : [];
  const myFormation: Formation = (auctionState?.myFormation as Formation) ?? "FOUR_FOUR_TWO";
  const opponentFormation: Formation = (auctionState?.opponentFormation as Formation) ?? "FOUR_FOUR_TWO";
  const error = actionError ?? syncError;

  // Budget: starting 100 minus spent (needed by deriveViewerFlags below)
  const spent = myTeam.reduce((sum, c) => sum + c.amountCents, 0);
  const budget = 100 - spent;

  const mySquadFull = myTeam.length >= 11;
  const isMyTurn = !!myUserId && !!round && round.currentTurnUserId === myUserId;
  const roundIsOpen = round?.status === "OPEN";

  // Derive viewer action flags locally so SSE-patched round keeps them accurate without a REST call.
  const { viewerCanBid, viewerCanTake, viewerCanPass, viewerCanFillCard } = deriveViewerFlags(
    round ?? null,
    myUserId,
    myTeam,
    myFormation,
    budget,
    serverClockOffsetMsRef.current
  );
  const showTakePanel = viewerCanTake;
  const couldBid = viewerCanBid;
  const canPass = viewerCanPass;
  const canFillCard = viewerCanFillCard;

  const iAmHighestBidder = !!myUserId && round?.highestBidderUserId === myUserId;
  const opponentHasBid = (round?.highestBidCents ?? 0) > 0;
  const waitingOnOpponent = roundIsOpen && !isMyTurn && !turnIsOpen;
  const showNoSlotBanner =
    roundIsOpen && !canFillCard && !showTakePanel && (isMyTurn || turnIsOpen || canPass);

  const turnStatusMessage = (): string => {
    if (mySquadFull) return "Squad full (11/11)";
    if (controlsLocked) return loading ? "Submitting…" : "Settling round…";
    if (inTimerGrace) return couldBid ? "Time's up — bids locked" : "Time's up — resolving turn…";
    if (waitingOnOpponent) {
      if (iAmHighestBidder && opponentHasBid) {
        return `You're leading at ${round!.highestBidCents} cr — opponent's turn`;
      }
      if (iAmHighestBidder) return "Waiting for opponent to bid";
      if (!canFillCard) return "No slot for this card — watching";
      return "Waiting for opponent…";
    }
    if (!canFillCard && canPass) return "No slot for this card — pass to skip";
    if (!canFillCard) return "No slot for this card — watching";
    if (turnIsOpen) return "Bidding open to both players!";
    if (isMyTurn) {
      if (opponentHasBid && !iAmHighestBidder) {
        return `Opponent leads at ${round!.highestBidCents} cr — your turn to bid`;
      }
      return "Your turn to bid!";
    }
    return "Waiting for opponent…";
  };
  const isResolvingRound = !!round && resolvingRoundId === round.roundId;
  // Lock only during our HTTP call, or when server broadcasts round_resolving (e.g. opponent passed).
  const controlsLocked = loading || isResolvingRound;
  const biddingLockedByGrace = inGracePeriod && secondsLeft <= 0 && !controlsLocked;
  const canBid = couldBid && !biddingLockedByGrace && !controlsLocked;
  const inTimerGrace = inGracePeriod && secondsLeft <= 0 && !timeExtended;
  const auctionPhaseActive = sessionSnap?.status === "AUCTIONING";

  // Minimum bid to win: current bid + 1 (or 1 if no bids yet)
  const minimumBid = round ? Math.max(1, round.highestBidCents + 1) : 1;

  // ---- Effects ----

  // 0. Persist session only after server confirms we are still in it
  useEffect(() => {
    if (sessionSnap?.joined && sessionId) {
      setActiveSessionId(sessionId);
    }
  }, [sessionSnap?.joined, sessionId]);

  // Stale localStorage or bookmarked URL: session gone from Redis → lobby
  useEffect(() => {
    if (!sessionNotFound) return;
    clearActiveSessionId();
    navigate("/lobby", { replace: true });
  }, [sessionNotFound, navigate]);

  // Navigation: redirect when session transitions to irrelevant phase
  useEffect(() => {
    if (!sessionSnap) return;
    if (sessionSnap.status === "FINISHED") { navigate(`/match/${sessionId}`); return; }
    const isActivePage = (s: string) =>
      s === "AUCTIONING" || s === "MATCH_START_PENDING" || s === "FINALIZING" || s === "AWAITING_RESULT";
    if (!isActivePage(sessionSnap.status)) navigate("/lobby");
  }, [sessionSnap?.status, navigate, sessionId]);

  // Navigation: match_completed push event or matchCompleted state
  useEffect(() => {
    if ((matchCompleted || lastEvent?.eventType === "match_completed") && sessionId) {
      navigate(`/match/${sessionId}`);
    }
  }, [matchCompleted, lastEvent, navigate, sessionId]);

  // Server clock offset: sync from event timestamps
  useEffect(() => {
    if (!lastEvent) return;
    const serverEventTime = Date.parse(lastEvent.occurredAt);
    if (!Number.isNaN(serverEventTime)) {
      serverClockOffsetMsRef.current = serverEventTime - Date.now();
    }
  }, [lastEvent]);

  // Lock controls as soon as server starts closing the round (e.g. opponent passed after your bid)
  useEffect(() => {
    if (!lastEvent) return;
    if (lastEvent.eventType === "round_resolving") {
      const payload = lastEvent.payload as { roundId?: string };
      if (payload.roundId) setResolvingRoundId(payload.roundId);
    }
    if (lastEvent.eventType === "round_ended" || lastEvent.eventType === "round_started") {
      setResolvingRoundId(null);
    }
  }, [lastEvent]);

  useEffect(() => {
    if (!round || round.status !== "OPEN") {
      setResolvingRoundId(null);
    }
  }, [round?.roundId, round?.status]);

  const beginRoundAction = () => {
    setLoading(true);
    setActionError(null);
  };

  const finishRoundAction = (updated: AuctionRoundResponse | null) => {
    setLoading(false);
    setResolvingRoundId(null);
    if (updated) applyActorRound(updated);
  };

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

  // 6. Round countdown timer + post-zero grace (bid lock; anti-snipe extension)
  useEffect(() => {
    const clearGrace = () => {
      if (graceTimeoutRef.current) {
        clearTimeout(graceTimeoutRef.current);
        graceTimeoutRef.current = null;
      }
      setInGracePeriod(false);
    };
    const startGrace = () => {
      clearGrace();
      setInGracePeriod(true);
      graceTimeoutRef.current = setTimeout(() => {
        graceTimeoutRef.current = null;
        setInGracePeriod(false);
      }, BID_UI_GRACE_MS);
    };

    if (!round) {
      setSecondsLeft(0);
      clearGrace();
      return;
    }

    const computeSecondsLeft = () => {
      const adjustedNow = Date.now() + serverClockOffsetMsRef.current;
      return Math.max(0, Math.ceil((new Date(round.endsAt).getTime() - adjustedNow) / 1000));
    };
    const initial = computeSecondsLeft();
    const roundChanged = countdownRoundIdRef.current !== round.roundId;
    const wasExpired = !roundChanged && previousSecondsLeftRef.current <= 0;

    // Anti-snipe: timer was at 0, same round gets a new endsAt (not on round open)
    if (!roundChanged && wasExpired && initial > 0) {
      clearGrace();
      setTimeExtended(true);
      setTimeout(() => setTimeExtended(false), 2000);
    }

    if (roundChanged) {
      countdownRoundIdRef.current = round.roundId;
      roundTotalSecondsRef.current = initial;
      setTimeExtended(false);
      clearGrace();
      previousSecondsLeftRef.current = initial;
    } else if (initial > roundTotalSecondsRef.current) {
      roundTotalSecondsRef.current = initial;
      if (initial > 0) clearGrace();
    } else if (!roundChanged && wasExpired && initial <= 0 && graceTimeoutRef.current == null) {
      startGrace();
    }

    setSecondsLeft(initial);
    if (!roundChanged) {
      previousSecondsLeftRef.current = initial;
    }
    const timer = setInterval(() => {
      const prev = previousSecondsLeftRef.current;
      const secs = computeSecondsLeft();
      if (prev > 0 && secs <= 0) {
        startGrace();
      }
      if (secs > 0) {
        clearGrace();
      }
      setSecondsLeft(secs);
      previousSecondsLeftRef.current = secs;
    }, 250);
    return () => clearInterval(timer);
  }, [round?.roundId, round?.endsAt]);

  // 6b. Track when bidding opens to both players
  useEffect(() => {
    if (!round || !round.turnOpenAt) {
      setTurnIsOpen(false);
      return;
    }
    const check = () => {
      const adjustedNow = Date.now() + serverClockOffsetMsRef.current;
      return adjustedNow >= new Date(round.turnOpenAt!).getTime();
    };
    if (check()) { setTurnIsOpen(true); return; }
    setTurnIsOpen(false);
    const timer = setInterval(() => {
      if (check()) { setTurnIsOpen(true); clearInterval(timer); }
    }, 250);
    return () => clearInterval(timer);
  }, [round?.roundId, round?.turnOpenAt]);

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

  // 8. REST-based keepalive ping (independent of WS/SSE health — used for forfeit detection).
  // 10s interval keeps us safely within the 30s connectionTracker threshold.
  useEffect(() => {
    if (!sessionId) return;
    pingSession(sessionId);
    const id = window.setInterval(() => pingSession(sessionId), 10_000);
    return () => window.clearInterval(id);
  }, [sessionId]);

  // ---- Handlers ----

  if (!sessionId) return <div className="p-8 text-white">Session ID missing</div>;

  const handleTake = async () => {
    if (!round) return;
    const takeAmount = Math.max(1, round.highestBidCents + 1);
    if (takeAmount > budget) {
      setActionError(`Insufficient budget. Need ${takeAmount} cr but only have ${budget} cr`);
      return;
    }
    beginRoundAction();
    try {
      const updated = await placeBid(sessionId, round.roundId, takeAmount);
      finishRoundAction(updated);
    } catch (e) {
      setActionError(e instanceof Error ? e.message : "Failed to take card");
      finishRoundAction(null);
    }
  };

  const handleBid = async () => {
    if (!round) { setActionError("Start a round first"); return; }
    if (myTeam.length >= 11) { setActionError("Your squad is full"); return; }
    if (!canBid) { setActionError("It is not your turn"); return; }
    const amountCents = Number(amount);
    if (!Number.isFinite(amountCents) || amountCents <= 0) { setActionError("Amount must be a positive number"); return; }
    if (amountCents > budget) { setActionError(`Insufficient budget. Maximum bid: ${budget} cr`); return; }
    beginRoundAction();
    try {
      const updated = await placeBid(sessionId, round.roundId, amountCents);
      finishRoundAction(updated);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Failed to place bid";
      if (msg.includes("Concurrent update") || msg.includes("409")) {
        try {
          const state = await getAuctionState(sessionId);
          finishRoundAction(state.currentRound);
        } catch {
          finishRoundAction(null);
        }
      } else {
        setActionError(msg);
        finishRoundAction(null);
      }
    }
  };

  const handlePassRound = async () => {
    if (!round) return;
    beginRoundAction();
    try {
      const updated = await passRound(sessionId, round.roundId);
      finishRoundAction(updated);
    } catch (e) {
      setActionError(e instanceof Error ? e.message : "Failed to pass round");
      finishRoundAction(null);
    }
  };

  const handleConfirmMatchStart = async () => {
    if (!sessionId) return;
    setLoading(true); setActionError(null);
    try { await confirmMatchStart(sessionId); }
    catch (e) { setActionError(e instanceof Error ? e.message : "Failed to confirm match start"); }
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

  const pitchHighlights = openEligibleHighlights(myFormation, myTeam, round?.eligiblePositions);

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
                highlightPositions={pitchHighlights}
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
              <div
                key={round.roundId}
                className="bg-surface-card rounded-xl border border-white/10 p-4 animate-fade-in"
              >
                <div className="text-center mb-4">
                  <PositionBadgeRow
                    primary={round.position}
                    eligiblePositions={round.eligiblePositions}
                    colors={positionColors}
                  />
                  <h2 className="text-2xl font-extrabold text-white mt-2">{round.cardName}</h2>
                  <p className="text-4xl font-black text-card-gold mt-1">{round.cardRating ?? "?"}</p>
                  <p className="text-xs text-slate-400 mt-1">Overall</p>
                  {round.pace != null && (
                    <PlayerAttributeBars
                      pace={round.pace}
                      shooting={round.shooting ?? 0}
                      passing={round.passing ?? 0}
                      defending={round.defending ?? 0}
                      physical={round.physical ?? 0}
                    />
                  )}
                </div>

                {/* Timer Bar */}
                <div className="mb-4">
                  <div className="flex justify-between text-xs text-slate-400 mb-1">
                    <span>Time Left</span>
                    <span className={`font-bold transition-colors duration-300 ${
                      timeExtended ? "text-accent-green" :
                      secondsLeft <= 0 ? "text-amber-400 animate-pulse" : 
                      secondsLeft <= 5 ? "text-red-400" : "text-white"
                    }`}>
                      {timeExtended ? `+${secondsLeft}s Extended!` : secondsLeft <= 0 ? "Time's up!" : `${secondsLeft}s`}
                    </span>
                  </div>
                  <div className="w-full h-2 bg-surface-elevated rounded-full overflow-hidden">
                    <div
                      className={`h-full rounded-full transition-all duration-300 ${
                        timeExtended ? "bg-accent-green" :
                        secondsLeft <= 0 ? "bg-amber-500" : timerColor
                      }`}
                      style={{ width: `${secondsLeft <= 0 ? 100 : timerPercent}%` }}
                    />
                  </div>
                </div>

                {/* Time extended notification */}
                {timeExtended && (
                  <div className="bg-green-900/30 border border-green-500/30 rounded-lg px-3 py-2 mb-4 text-center animate-pulse">
                    <p className="text-accent-green text-sm font-medium">Bid extended the round! Keep bidding!</p>
                  </div>
                )}

                {/* Current Bid */}
                <div className="bg-surface-elevated rounded-lg p-3 mb-4 text-center transition-all duration-300">
                  <p className="text-xs text-slate-400">Highest Bid</p>
                  <p
                    key={round.highestBidCents}
                    className="text-2xl font-bold text-white animate-bid-pop"
                  >
                    {round.highestBidCents > 0
                      ? `${round.highestBidCents} cr`
                      : "No bids"}
                  </p>
                  <p className="text-xs text-slate-400 mt-1 transition-opacity duration-300">
                    {ROUND_STATUS_LABELS[round.status] ?? round.status}
                  </p>
                </div>

                {/* Bid controls — visible but locked while submitting */}
                {roundIsOpen && (
                  <div
                    className={`relative space-y-3 rounded-lg transition-opacity ${
                      controlsLocked ? "pointer-events-none opacity-45" : ""
                    }`}
                  >
                    {controlsLocked && (
                      <div className="absolute inset-0 z-10 flex items-center justify-center rounded-lg bg-black/25">
                        <p className="text-amber-400 text-sm font-semibold animate-pulse px-3 py-1 rounded bg-black/40">
                          {loading ? "Submitting…" : "Settling round…"}
                        </p>
                      </div>
                    )}
                    {showNoSlotBanner && (
                      <div className="bg-slate-800/80 border border-slate-500/30 rounded-lg px-3 py-2 text-center">
                        <p className="text-slate-300 text-sm font-medium">
                          No open slot for this card in your formation
                        </p>
                      </div>
                    )}
                    {waitingOnOpponent && canFillCard && iAmHighestBidder && opponentHasBid && (
                      <div className="bg-accent/10 border border-accent/30 rounded-lg px-3 py-2 text-center">
                        <p className="text-accent text-sm font-medium">
                          You lead at {round.highestBidCents} cr — waiting for opponent
                        </p>
                      </div>
                    )}
                    {showTakePanel ? (
                      <>
                    <div className="bg-amber-900/20 border border-amber-500/30 rounded-lg px-3 py-2 text-center">
                      <p className="text-amber-400 text-sm font-medium">Opponent can't bid on this card</p>
                    </div>
                    <button
                      onClick={handleTake}
                      disabled={controlsLocked || minimumBid > budget}
                      className="w-full bg-accent-green hover:bg-green-600 disabled:opacity-40 text-white font-bold py-3 rounded-lg transition-colors text-lg"
                    >
                      Take for {minimumBid} cr
                    </button>
                    <button
                      onClick={handlePassRound}
                      disabled={controlsLocked}
                      className="w-full bg-red-900/30 border border-red-500/30 text-red-400 hover:bg-red-900/50 disabled:opacity-40 py-2 rounded-lg transition-colors text-sm font-medium"
                    >
                      Pass
                    </button>
                      </>
                    ) : couldBid ? (
                      <>
                        <div className="flex gap-2">
                          <input
                            value={amount}
                        onChange={(e) => setAmount(e.target.value)}
                        type="number"
                        min={1}
                        max={budget}
                        placeholder="Bid (cr)"
                        disabled={controlsLocked || mySquadFull || (!canBid && !controlsLocked)}
                        readOnly={controlsLocked}
                        className="flex-1 bg-surface-elevated border border-white/10 rounded-lg px-3 py-2 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-accent disabled:opacity-40"
                      />
                      <button
                        onClick={() => {
                          const current = Number(amount);
                          const safe = Number.isFinite(current) ? current : Math.max(1, (round?.highestBidCents ?? 0) + 1);
                          setAmount(String(Math.min(safe + 1, budget)));
                        }}
                        disabled={controlsLocked || mySquadFull || (!canBid && !controlsLocked)}
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
                        disabled={controlsLocked || mySquadFull || (!canBid && !controlsLocked)}
                        className="bg-surface-elevated border border-white/10 text-white px-3 py-2 rounded-lg hover:bg-white/10 disabled:opacity-40 transition-colors"
                      >
                        +5
                      </button>
                    </div>

                    <button
                      onClick={handleBid}
                      disabled={controlsLocked || mySquadFull || (!canBid && !controlsLocked)}
                      className="w-full bg-accent hover:bg-blue-600 disabled:opacity-40 text-white font-bold py-3 rounded-lg transition-colors"
                    >
                      Place Bid
                    </button>

                    {myUserId && round.highestBidderUserId === myUserId ? (
                      <button
                        onClick={handlePassRound}
                        disabled={controlsLocked || (!canBid && !controlsLocked)}
                        className="w-full bg-slate-700/50 border border-slate-500/30 text-slate-300 hover:bg-slate-700/70 disabled:opacity-40 py-2 rounded-lg transition-colors text-sm font-medium"
                      >
                        End Turn
                      </button>
                    ) : (
                      <button
                        onClick={handlePassRound}
                        disabled={controlsLocked || (!canBid && !controlsLocked)}
                        className="w-full bg-red-900/30 border border-red-500/30 text-red-400 hover:bg-red-900/50 disabled:opacity-40 py-2 rounded-lg transition-colors text-sm font-medium"
                      >
                        Pass
                      </button>
                    )}
                      </>
                    ) : canPass ? (
                      <button
                        onClick={handlePassRound}
                        disabled={controlsLocked}
                        className="w-full bg-red-900/30 border border-red-500/30 text-red-400 hover:bg-red-900/50 disabled:opacity-40 py-2 rounded-lg transition-colors text-sm font-medium"
                      >
                        Pass
                      </button>
                    ) : null}
                  </div>
                )}

                {/* Turn Indicator */}
                <div className={`mt-3 text-center text-sm font-medium py-2 rounded-lg transition-colors duration-300 ${
                  mySquadFull
                    ? "bg-accent-green/10 text-accent-green"
                    : controlsLocked
                      ? "bg-amber-500/10 text-amber-400"
                    : inTimerGrace
                      ? "bg-amber-500/10 text-amber-400"
                      : turnIsOpen
                        ? "bg-amber-500/10 text-amber-400"
                        : isMyTurn
                          ? "bg-accent/10 text-accent"
                          : "bg-surface-elevated text-slate-400"
                }`}>
                  {turnStatusMessage()}
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
