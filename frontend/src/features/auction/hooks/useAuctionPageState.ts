import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  AuctionRoundResponse,
  AuctionTeamCardResponse,
  confirmMatchStart,
  getAuctionState,
  getClientUserId,
  passRound,
  pingSession,
  placeBid,
} from "../../../shared/api";
import { clearActiveSessionId, setActiveSessionId } from "../../../shared/session";
import { useSessionSync } from "../../../shared/realtime/useSessionSync";
import type { Formation } from "../../../shared/api/contracts";
import { deriveViewerFlags } from "../deriveViewerFlags";
import {
  buildRoundOutcomeNotice,
  type RoundEndedPayload,
  type RoundOutcomeNotice,
} from "../buildRoundOutcomeNotice";
import { getOrCreateClientSession } from "../../../shared/session";

export const BID_UI_GRACE_MS = 2000;

export const POSITION_COLORS: Record<string, string> = {
  GK: "bg-yellow-500",
  DEF: "bg-blue-500",
  CDM: "bg-indigo-500",
  CM: "bg-green-500",
  CAM: "bg-orange-500",
  FWD: "bg-red-500",
};

function mapCards(raw: AuctionTeamCardResponse[]): AuctionTeamCardResponse[] {
  return raw.map((c) => ({
    cardId: c.cardId,
    cardName: c.cardName,
    position: c.position,
    rating: c.rating ?? 0,
    amountCents: c.amountCents,
  }));
}

export function useAuctionPageState(sessionId: string | undefined) {
  const navigate = useNavigate();
  const myUserId = getClientUserId();

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
  const [resolvingRoundId, setResolvingRoundId] = useState<string | null>(null);
  const [roundOutcomeNotice, setRoundOutcomeNotice] = useState<RoundOutcomeNotice | null>(null);
  const lastOutcomeRoundIdRef = useRef<string | null>(null);
  const outcomeDismissRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const {
    snapshot: sessionSnap,
    auctionState,
    round,
    applyActorRound,
    isConnected,
    lastEvent,
    error: syncError,
    sessionNotFound,
    matchCompleted,
  } = useSessionSync(sessionId ?? null);

  const multiPlayerMode = (sessionSnap?.maxPlayers ?? 2) > 2;
  const playerViews = auctionState?.players ?? [];
  const myPlayerView = playerViews.find((p) => p.userId === myUserId);

  const myTeam = multiPlayerMode && myPlayerView
    ? mapCards(myPlayerView.team)
    : auctionState ? mapCards(auctionState.myTeam) : [];
  const opponentTeam = multiPlayerMode
    ? []
    : auctionState ? mapCards(auctionState.opponentTeam) : [];
  const myFormation: Formation = (multiPlayerMode && myPlayerView
    ? myPlayerView.formation
    : auctionState?.myFormation) as Formation ?? "FOUR_FOUR_TWO";
  const opponentFormation: Formation = (auctionState?.opponentFormation as Formation) ?? "FOUR_FOUR_TWO";
  const error = actionError ?? syncError;

  const spent = myTeam.reduce((sum, c) => sum + c.amountCents, 0);
  const budget = multiPlayerMode && myPlayerView
    ? Math.round(myPlayerView.budgetRemaining / 100)
    : 100 - spent;
  const currentTurnNickname =
    playerViews.find((p) => p.currentTurn)?.nickname
    ?? (round?.currentTurnUserId
      ? sessionSnap?.players?.find((p) => p.userId === round.currentTurnUserId)?.nickname
      : null);
  const mySquadFull = myTeam.length >= 11;
  const isMyTurn = !!myUserId && !!round && round.currentTurnUserId === myUserId;
  const roundIsOpen = round?.status === "OPEN" ?? false;

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
  const isResolvingRound = !!round && resolvingRoundId === round.roundId;
  const controlsLocked = loading || isResolvingRound;
  const biddingLockedByGrace = inGracePeriod && secondsLeft <= 0 && !controlsLocked;
  const canBid = couldBid && !biddingLockedByGrace && !controlsLocked;
  const inTimerGrace = inGracePeriod && secondsLeft <= 0 && !timeExtended;
  const auctionPhaseActive = sessionSnap?.status === "AUCTIONING";
  const minimumBid = round ? Math.max(1, round.highestBidCents + 1) : 1;

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

  useEffect(() => {
    if (sessionSnap?.joined && sessionId) {
      setActiveSessionId(sessionId);
    }
  }, [sessionSnap?.joined, sessionId]);

  useEffect(() => {
    if (!sessionNotFound) return;
    clearActiveSessionId();
    navigate("/lobby", { replace: true });
  }, [sessionNotFound, navigate]);

  const gameMode = sessionSnap?.gameMode ?? "DUEL";

  useEffect(() => {
    if (!sessionSnap || !sessionId) return;
    if (gameMode === "DUEL" && sessionSnap.status === "FINISHED") {
      navigate(`/match/${sessionId}`);
      return;
    }
    const isActivePage = (s: string) =>
      s === "AUCTIONING" || s === "MATCH_START_PENDING" || s === "FINALIZING" || s === "AWAITING_RESULT";
    if (!isActivePage(sessionSnap.status) && gameMode === "DUEL") navigate("/lobby");
  }, [sessionSnap?.status, gameMode, navigate, sessionId]);

  useEffect(() => {
    if (!sessionId || !lastEvent) return;
    if (lastEvent.eventType === "tournament_completed") {
      navigate(`/tournament/${sessionId}`);
      return;
    }
    if (lastEvent.eventType === "league_pending") {
      navigate(`/league-pending/${sessionId}`);
      return;
    }
    if (gameMode === "DUEL" && (matchCompleted || lastEvent.eventType === "match_completed")) {
      navigate(`/match/${sessionId}`);
    }
  }, [matchCompleted, lastEvent, gameMode, navigate, sessionId]);

  useEffect(() => {
    if (!lastEvent) return;
    const serverEventTime = Date.parse(lastEvent.occurredAt);
    if (!Number.isNaN(serverEventTime)) {
      serverClockOffsetMsRef.current = serverEventTime - Date.now();
    }
  }, [lastEvent]);

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
    if (!lastEvent || lastEvent.eventType !== "round_ended") return;

    const notice = buildRoundOutcomeNotice(
      lastEvent.payload as RoundEndedPayload,
      myUserId,
      getOrCreateClientSession().nickname
    );
    if (!notice || notice.roundId === lastOutcomeRoundIdRef.current) return;

    lastOutcomeRoundIdRef.current = notice.roundId;
    setRoundOutcomeNotice(notice);

    if (outcomeDismissRef.current) {
      clearTimeout(outcomeDismissRef.current);
    }
    const ms = notice.kind === "sold" ? 4500 : 3200;
    outcomeDismissRef.current = setTimeout(() => {
      setRoundOutcomeNotice(null);
      outcomeDismissRef.current = null;
    }, ms);

    return () => {
      if (outcomeDismissRef.current) {
        clearTimeout(outcomeDismissRef.current);
        outcomeDismissRef.current = null;
      }
    };
  }, [lastEvent, myUserId]);

  useEffect(() => {
    if (lastEvent?.eventType === "round_started") {
      setRoundOutcomeNotice(null);
    }
  }, [lastEvent]);

  useEffect(() => {
    if (!round || round.status !== "OPEN") {
      setResolvingRoundId(null);
    }
  }, [round?.roundId, round?.status]);

  useEffect(() => {
    if (!round) return;
    const minimum = Math.max(1, round.highestBidCents + 1);
    const roundChanged = previousRoundIdRef.current !== round.roundId;
    if (roundChanged) {
      previousRoundIdRef.current = round.roundId;
      setAmount(String(minimum));
      return;
    }
    setAmount((prev) => {
      const parsed = Number(prev);
      if (!Number.isFinite(parsed) || parsed < minimum) return String(minimum);
      return prev;
    });
  }, [round]);

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

  useEffect(() => {
    if (!round || !round.turnOpenAt) {
      setTurnIsOpen(false);
      return;
    }
    const check = () => {
      const adjustedNow = Date.now() + serverClockOffsetMsRef.current;
      return adjustedNow >= new Date(round.turnOpenAt!).getTime();
    };
    if (check()) {
      setTurnIsOpen(true);
      return;
    }
    setTurnIsOpen(false);
    const timer = setInterval(() => {
      if (check()) {
        setTurnIsOpen(true);
        clearInterval(timer);
      }
    }, 250);
    return () => clearInterval(timer);
  }, [round?.roundId, round?.turnOpenAt]);

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
    const timer = window.setInterval(() => setMatchStartSecondsLeft(tick()), 250);
    return () => window.clearInterval(timer);
  }, [sessionSnap?.matchStartDeadlineAt]);

  useEffect(() => {
    if (!sessionId) return;
    pingSession(sessionId);
    const id = window.setInterval(() => pingSession(sessionId), 10_000);
    return () => window.clearInterval(id);
  }, [sessionId]);

  const beginRoundAction = () => {
    setLoading(true);
    setActionError(null);
  };

  const finishRoundAction = (updated: AuctionRoundResponse | null) => {
    setLoading(false);
    setResolvingRoundId(null);
    if (updated) applyActorRound(updated);
  };

  const handleTake = async () => {
    if (!round || !sessionId) return;
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
    if (!sessionId) return;
    if (!round) {
      setActionError("Start a round first");
      return;
    }
    if (myTeam.length >= 11) {
      setActionError("Your squad is full");
      return;
    }
    if (!canBid) {
      setActionError("It is not your turn");
      return;
    }
    const amountCents = Number(amount);
    if (!Number.isFinite(amountCents) || amountCents <= 0) {
      setActionError("Amount must be a positive number");
      return;
    }
    if (amountCents > budget) {
      setActionError(`Insufficient budget. Maximum bid: ${budget} cr`);
      return;
    }
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
    if (!round || !sessionId) return;
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
    setLoading(true);
    setActionError(null);
    try {
      await confirmMatchStart(sessionId);
    } catch (e) {
      setActionError(e instanceof Error ? e.message : "Failed to confirm match start");
    } finally {
      setLoading(false);
    }
  };

  const timerPercent =
    round && roundTotalSecondsRef.current > 0
      ? Math.min(100, (secondsLeft / roundTotalSecondsRef.current) * 100)
      : 0;
  const timerColor = secondsLeft <= 5 ? "bg-red-500" : secondsLeft <= 10 ? "bg-amber-500" : "bg-accent";
  const budgetPercent = Math.max(0, (budget / 100) * 100);

  return {
    myUserId,
    sessionSnap,
    round,
    error,
    isConnected,
    myTeam,
    opponentTeam,
    myFormation,
    opponentFormation,
    budget,
    budgetPercent,
    amount,
    setAmount,
    secondsLeft,
    timeExtended,
    matchStartSecondsLeft,
    auctionPhaseActive,
    roundIsOpen,
    minimumBid,
    controlsLocked,
    canBid,
    couldBid,
    canPass,
    canFillCard,
    showTakePanel,
    showNoSlotBanner,
    waitingOnOpponent,
    iAmHighestBidder,
    opponentHasBid,
    mySquadFull,
    turnIsOpen,
    isMyTurn,
    inTimerGrace,
    loading,
    timerPercent,
    timerColor,
    turnStatusMessage,
    handleTake,
    handleBid,
    handlePassRound,
    handleConfirmMatchStart,
    roundOutcomeNotice,
    multiPlayerMode,
    playerViews,
    currentTurnNickname,
    gameMode,
  };
}
