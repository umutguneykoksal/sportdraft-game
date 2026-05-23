import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getMatchResult } from "../../../shared/api";
import { ApiError } from "../../../shared/api/errors";
import { useSessionStream } from "../../../shared/sse";
import { MATCH_RESULT_POLL_MS, SIMULATION_STALL_WARNING_MS } from "../../../shared/realtime/syncConstants";
import type { AuctionTeamCardResponse, Formation, MatchResultResponse } from "../../../shared/api/contracts";

const MAX_CONSECUTIVE_ERRORS = 5;
const FATAL_HTTP_STATUSES = new Set([401, 403]);

export function useMatchFlow(sessionId: string | undefined) {
  const { events } = useSessionStream(sessionId ?? null);
  const [result, setResult] = useState<MatchResultResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const loadedRef = useRef(false);
  const stoppedRef = useRef(false);
  const consecutiveErrorsRef = useRef(0);
  const waitingSinceRef = useRef<number | null>(null);

  const stopWithError = useCallback((message: string) => {
    stoppedRef.current = true;
    setLoadError(message);
  }, []);

  const fetchResult = useCallback(async () => {
    if (!sessionId || loadedRef.current || stoppedRef.current) return;
    try {
      const res = await getMatchResult(sessionId);
      if (res) {
        loadedRef.current = true;
        consecutiveErrorsRef.current = 0;
        waitingSinceRef.current = null;
        setLoadError(null);
        setResult(res);
        return;
      }
      consecutiveErrorsRef.current = 0;
    } catch (e) {
      if (e instanceof ApiError && FATAL_HTTP_STATUSES.has(e.status)) {
        stopWithError(e.message);
        return;
      }
      consecutiveErrorsRef.current += 1;
      if (consecutiveErrorsRef.current >= MAX_CONSECUTIVE_ERRORS) {
        stopWithError(
          e instanceof Error ? e.message : "Could not load match result."
        );
      }
    }
  }, [sessionId, stopWithError]);

  useEffect(() => {
    loadedRef.current = false;
    stoppedRef.current = false;
    consecutiveErrorsRef.current = 0;
    waitingSinceRef.current = null;
    setResult(null);
    setLoadError(null);
  }, [sessionId]);

  useEffect(() => {
    fetchResult();
  }, [fetchResult]);

  const matchCompletedReceived = useMemo(
    () => events.some((e) => e.eventType === "match_completed"),
    [events],
  );

  useEffect(() => {
    if (matchCompletedReceived && waitingSinceRef.current === null) {
      waitingSinceRef.current = Date.now();
    }
  }, [matchCompletedReceived]);

  useEffect(() => {
    if (matchCompletedReceived) fetchResult();
  }, [matchCompletedReceived, fetchResult]);

  useEffect(() => {
    if (!matchCompletedReceived || loadedRef.current || stoppedRef.current) return;

    const id = window.setInterval(() => {
      const since = waitingSinceRef.current;
      if (
        since != null &&
        Date.now() - since >= SIMULATION_STALL_WARNING_MS &&
        !loadedRef.current &&
        !stoppedRef.current
      ) {
        stopWithError(
          "Match result is taking longer than expected. Please return to the lobby and try again."
        );
      }
    }, 5_000);

    return () => window.clearInterval(id);
  }, [matchCompletedReceived, stopWithError]);

  useEffect(() => {
    if (!sessionId || loadedRef.current || stoppedRef.current) return;

    const id = window.setInterval(() => {
      if (!loadedRef.current && !stoppedRef.current) fetchResult();
    }, MATCH_RESULT_POLL_MS);

    return () => window.clearInterval(id);
  }, [sessionId, fetchResult, matchCompletedReceived]);

  const ready = useMemo(() => result !== null, [result]);

  return {
    ready,
    loadError,
    homeScore: result?.homeScore ?? null,
    awayScore: result?.awayScore ?? null,
    myTeam: result?.myTeam ?? [],
    opponentTeam: result?.opponentTeam ?? [],
    myFormation: (result?.myFormation ?? "FOUR_FOUR_TWO") as Formation,
    opponentFormation: (result?.opponentFormation ?? "FOUR_FOUR_TWO") as Formation,
    myNickname: result?.myNickname ?? "You",
    opponentNickname: result?.opponentNickname ?? "Opponent",
    homeUserId: result?.homeUserId ?? null,
    awayUserId: result?.awayUserId ?? null,
    sessionHomeNickname: result?.sessionHomeNickname ?? result?.myNickname ?? "Home",
    sessionAwayNickname: result?.sessionAwayNickname ?? result?.opponentNickname ?? "Away",
    sessionHomeScore: result?.sessionHomeScore ?? result?.homeScore ?? 0,
    sessionAwayScore: result?.sessionAwayScore ?? result?.awayScore ?? 0,
    sessionHomePenaltyScore: result?.sessionHomePenaltyScore ?? 0,
    sessionAwayPenaltyScore: result?.sessionAwayPenaltyScore ?? 0,
    myPenaltyScore: result?.myPenaltyScore ?? 0,
    opponentPenaltyScore: result?.opponentPenaltyScore ?? 0,
    winnerUserId: result?.winnerUserId ?? null,
    myShots: result?.myShots ?? 0,
    opponentShots: result?.opponentShots ?? 0,
    myFouls: result?.myFouls ?? 0,
    opponentFouls: result?.opponentFouls ?? 0,
  };
}
