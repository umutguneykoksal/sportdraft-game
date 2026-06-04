import { useCallback, useEffect, useRef, useState } from "react";
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
import { deriveRoundTimerEffects } from "../deriveRoundTimerState";
import { buildDuelTurnOrderEntries, buildTurnOrderEntries, type TurnOrderEntry } from "../AuctionTurnOrderBar";
import { buildRoundOutcomeNotice, type RoundOutcomeNotice } from "../buildRoundOutcomeNotice";
import { ROUND_BID_SECONDS } from "../auctionTiming";
import type { RoundEndedPayload } from "../../../shared/realtime/eventDispatch";
import { getOrCreateClientSession } from "../../../shared/session";
import { mapAuctionTeamCards, POSITION_COLORS } from "../../../shared/auction/teamCards";

export { POSITION_COLORS };

export const BID_UI_GRACE_MS = 2000;
export const ROUND_OUTCOME_TOAST_MS = 5000;

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
  const [inCardReveal, setInCardReveal] = useState(false);
  const previousRoundIdRef = useRef<string | null>(null);
  const countdownRoundIdRef = useRef<string | null>(null);
  const previousSecondsLeftRef = useRef<number>(0);
  const graceTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const serverClockOffsetMsRef = useRef<number>(0);
  const roundTotalSecondsRef = useRef<number>(ROUND_BID_SECONDS);
  const [turnIsOpen, setTurnIsOpen] = useState(false);
  const [secondsUntilTurnOpen, setSecondsUntilTurnOpen] = useState<number | null>(null);
  const [resolvingRoundId, setResolvingRoundId] = useState<string | null>(null);
  const [roundOutcomeNotice, setRoundOutcomeNotice] = useState<RoundOutcomeNotice | null>(null);
  const lastOutcomeRoundIdRef = useRef<string | null>(null);
  const outcomeDismissRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const playerNicksRef = useRef<{ userId: string; nickname: string }[]>([]);

  const showRoundOutcome = useCallback(
    (payload: RoundEndedPayload) => {
      const notice = buildRoundOutcomeNotice(
        payload,
        myUserId,
        getOrCreateClientSession().nickname,
        playerNicksRef.current
      );
      if (!notice || notice.roundId === lastOutcomeRoundIdRef.current) return;

      lastOutcomeRoundIdRef.current = notice.roundId;
      setRoundOutcomeNotice(notice);

      if (outcomeDismissRef.current) {
        clearTimeout(outcomeDismissRef.current);
      }
      outcomeDismissRef.current = setTimeout(() => {
        setRoundOutcomeNotice(null);
        outcomeDismissRef.current = null;
      }, ROUND_OUTCOME_TOAST_MS);
    },
    [myUserId]
  );

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
  } = useSessionSync(sessionId ?? null, false, { onRoundEnded: showRoundOutcome });

  const wasEverConnectedRef = useRef(false);
  useEffect(() => {
    if (isConnected) wasEverConnectedRef.current = true;
  }, [isConnected]);
  const showReconnecting = wasEverConnectedRef.current && !isConnected;

  const multiPlayerMode = (sessionSnap?.maxPlayers ?? 2) > 2;
  const playerViews = auctionState?.players ?? [];
  const myPlayerView = playerViews.find((p) => p.userId === myUserId);

  const myTeam = multiPlayerMode && myPlayerView
    ? mapAuctionTeamCards(myPlayerView.team)
    : auctionState ? mapAuctionTeamCards(auctionState.myTeam) : [];
  const opponentTeam = multiPlayerMode
    ? []
    : auctionState ? mapAuctionTeamCards(auctionState.opponentTeam) : [];
  const myFormation: Formation = (multiPlayerMode && myPlayerView
    ? myPlayerView.formation
    : auctionState?.myFormation) as Formation ?? "FOUR_FOUR_TWO";
  const opponentFormation: Formation = (auctionState?.opponentFormation as Formation) ?? "FOUR_FOUR_TWO";
  const error = actionError ?? syncError;

  const spent = myTeam.reduce((sum, c) => sum + c.amountCents, 0);
  // Budget is in the same minor units as highestBidCents (starting budget 100).
  const budget = multiPlayerMode && myPlayerView
    ? myPlayerView.budgetRemaining
    : 100 - spent;
  const currentTurnNickname =
    playerViews.find((p) => p.currentTurn)?.nickname
    ?? (round?.currentTurnUserId
      ? sessionSnap?.players?.find((p) => p.userId === round.currentTurnUserId)?.nickname
      : null);

  const opponentSpent = opponentTeam.reduce((sum, c) => sum + c.amountCents, 0);
  const opponentBudget = 100 - opponentSpent;

  const turnOrderEntries: TurnOrderEntry[] = multiPlayerMode
    ? buildTurnOrderEntries(
        playerViews,
        sessionSnap?.players,
        round?.currentTurnUserId,
        myUserId
      )
    : buildDuelTurnOrderEntries(
        sessionSnap?.players,
        round?.currentTurnUserId,
        myUserId,
        budget,
        myTeam.length,
        opponentBudget,
        opponentTeam.length
      );
  const mySquadFull = myTeam.length >= 11;
  const isMyTurn = !!myUserId && !!round && round.currentTurnUserId === myUserId;
  const roundIsOpen = round?.status === "OPEN" ?? false;

  const { viewerCanBid, viewerCanTake, viewerCanPass, viewerCanFillCard } = deriveViewerFlags(
    round ?? null,
    myUserId,
    myTeam,
    myFormation,
    budget,
    serverClockOffsetMsRef.current,
    turnIsOpen
  );
  const showTakePanel = viewerCanTake;
  const couldBid = viewerCanBid;
  const canPass = viewerCanPass;
  const canFillCard = viewerCanFillCard;
  const iAmHighestBidder = !!myUserId && round?.highestBidderUserId === myUserId;
  const opponentHasBid = (round?.highestBidCents ?? 0) > 0;
  const waitingOnOpponent =
    roundIsOpen &&
    (iAmHighestBidder && opponentHasBid
      ? true
      : !isMyTurn && !turnIsOpen);
  const showNoSlotBanner =
    roundIsOpen && !canFillCard && !showTakePanel && (isMyTurn || turnIsOpen || canPass);
  const isResolvingRound = !!round && resolvingRoundId === round.roundId;
  const controlsLocked = loading || isResolvingRound;
  const interactionLocked = controlsLocked || inCardReveal;
  const biddingLockedByGrace = inGracePeriod && secondsLeft <= 0 && !interactionLocked;
  const canBid = couldBid && !biddingLockedByGrace && !interactionLocked;
  const inTimerGrace = inGracePeriod && secondsLeft <= 0 && !timeExtended;
  const auctionPhaseActive = sessionSnap?.status === "AUCTIONING";
  const minimumBid = round ? Math.max(1, round.highestBidCents + 1) : 1;

  const turnStatusMessage = (): string => {
    if (mySquadFull) return "Squad full (11/11)";
    if (inCardReveal) return "New card — bidding opens soon";
    if (controlsLocked) return loading ? "Submitting…" : "Settling round…";
    if (inTimerGrace) return couldBid ? "Time's up — bids locked" : "Time's up — resolving turn…";
    if (waitingOnOpponent) {
      if (secondsUntilTurnOpen != null && secondsUntilTurnOpen > 0 && !turnIsOpen) {
        const finalPhase = secondsLeft <= 15;
        return multiPlayerMode
          ? finalPhase
            ? `Final ${secondsLeft}s — open to all with a slot`
            : `Bids open to all in ${secondsUntilTurnOpen}s`
          : finalPhase
            ? `Final ${secondsLeft}s — both can bid`
            : `Open bidding in ${secondsUntilTurnOpen}s`;
      }
      if (iAmHighestBidder && opponentHasBid) {
        return multiPlayerMode
          ? `You're leading at ${round!.highestBidCents} cr — next player's turn`
          : `You're leading at ${round!.highestBidCents} cr — opponent's turn`;
      }
      if (iAmHighestBidder) {
        return multiPlayerMode ? "Waiting for next player to bid" : "Waiting for opponent to bid";
      }
      if (!canFillCard) return "No slot for this card — watching";
      return multiPlayerMode ? "Waiting for turn or open bidding…" : "Waiting for opponent…";
    }
    if (!canFillCard && canPass) return "No slot for this card — pass to skip";
    if (!canFillCard) return "No slot for this card — watching";
    if (turnIsOpen) {
      if (iAmHighestBidder && opponentHasBid) {
        return multiPlayerMode
          ? `You're leading at ${round!.highestBidCents} cr — waiting for others to pass or outbid`
          : `You're leading at ${round!.highestBidCents} cr — waiting for opponent`;
      }
      return multiPlayerMode
        ? "Bidding open to all players with a slot for this card!"
        : "Bidding open to both players!";
    }
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
    if (
      lastEvent.eventType === "tournament_started"
      || lastEvent.eventType === "tournament_updated"
      || lastEvent.eventType === "tournament_completed"
    ) {
      navigate(`/tournament/${sessionId}`);
      return;
    }
    if (
      lastEvent.eventType === "league_started"
      || lastEvent.eventType === "league_updated"
      || lastEvent.eventType === "league_completed"
      || lastEvent.eventType === "league_playoff_started"
    ) {
      navigate(`/league/${sessionId}`);
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
    const fromAuction = auctionState?.players?.map((p) => ({
      userId: p.userId,
      nickname: p.nickname,
    }));
    const fromLobby = sessionSnap?.players?.map((p) => ({
      userId: p.userId,
      nickname: p.nickname,
    }));
    playerNicksRef.current = fromAuction?.length ? fromAuction : fromLobby ?? [];
  }, [auctionState?.players, sessionSnap?.players]);

  useEffect(() => {
    return () => {
      if (outcomeDismissRef.current) {
        clearTimeout(outcomeDismissRef.current);
        outcomeDismissRef.current = null;
      }
    };
  }, []);

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

    const applyTimerEffects = () => {
      if (countdownRoundIdRef.current !== round.roundId) {
        setTimeExtended(false);
      }

      const effects = deriveRoundTimerEffects({
        roundId: round.roundId,
        startsAt: round.startsAt ?? null,
        endsAt: round.endsAt,
        previousRoundId: countdownRoundIdRef.current,
        previousSecondsLeft: previousSecondsLeftRef.current,
        roundTotalSeconds: roundTotalSecondsRef.current,
        serverClockOffsetMs: serverClockOffsetMsRef.current,
        hasGraceTimeout: graceTimeoutRef.current != null,
      });

      setInCardReveal(effects.inCardReveal);

      if (effects.clearGrace) {
        clearGrace();
      }
      if (effects.timeExtended) {
        setTimeExtended(true);
        setTimeout(() => setTimeExtended(false), 2000);
      }
      if (effects.startGrace) {
        startGrace();
      }

      countdownRoundIdRef.current = effects.previousRoundId;
      roundTotalSecondsRef.current = effects.roundTotalSeconds;
      previousSecondsLeftRef.current = effects.secondsLeft;
      setSecondsLeft(effects.secondsLeft);
    };

    applyTimerEffects();

    const timer = setInterval(applyTimerEffects, 250);
    return () => clearInterval(timer);
  }, [round?.roundId, round?.endsAt, round?.startsAt]);

  useEffect(() => {
    if (!round || !round.turnOpenAt) {
      setTurnIsOpen(false);
      setSecondsUntilTurnOpen(null);
      return;
    }
    const openAtMs = new Date(round.turnOpenAt).getTime();
    const tick = () => {
      const adjustedNow = Date.now() + serverClockOffsetMsRef.current;
      const open = adjustedNow >= openAtMs;
      setTurnIsOpen(open);
      setSecondsUntilTurnOpen(open ? null : Math.max(0, Math.ceil((openAtMs - adjustedNow) / 1000)));
      return open;
    };
    if (tick()) return;
    const timer = setInterval(() => {
      if (tick()) clearInterval(timer);
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
    showReconnecting,
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
    interactionLocked,
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
    inCardReveal,
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
    turnOrderEntries,
    currentTurnNickname,
    gameMode,
    secondsUntilTurnOpen,
  };
}
