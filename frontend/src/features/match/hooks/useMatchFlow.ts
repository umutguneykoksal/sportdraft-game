import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getMatchResult } from "../../../shared/api";
import { useSessionStream } from "../../../shared/sse";
import { MATCH_RESULT_POLL_MS } from "../../../shared/realtime/syncConstants";
import type { AuctionTeamCardResponse, Formation, MatchResultResponse } from "../../../shared/api/contracts";

export function useMatchFlow(sessionId: string | undefined) {
  const { events } = useSessionStream(sessionId ?? null);
  const [result, setResult] = useState<MatchResultResponse | null>(null);
  const loadedRef = useRef(false);

  const fetchResult = useCallback(async () => {
    if (!sessionId || loadedRef.current) return;
    try {
      const res = await getMatchResult(sessionId);
      if (res) {
        loadedRef.current = true;
        setResult(res);
      }
    } catch {
      // retry via poll until result is ready
    }
  }, [sessionId]);

  useEffect(() => {
    loadedRef.current = false;
    setResult(null);
  }, [sessionId]);

  useEffect(() => {
    fetchResult();
  }, [fetchResult]);

  const matchCompletedReceived = useMemo(
    () => events.some((e) => e.eventType === "match_completed"),
    [events],
  );

  useEffect(() => {
    if (matchCompletedReceived) fetchResult();
  }, [matchCompletedReceived, fetchResult]);

  useEffect(() => {
    if (!sessionId || loadedRef.current) return;

    const id = window.setInterval(() => {
      if (!loadedRef.current) fetchResult();
    }, MATCH_RESULT_POLL_MS);

    return () => window.clearInterval(id);
  }, [sessionId, fetchResult, matchCompletedReceived]);

  const ready = useMemo(() => result !== null, [result]);

  return {
    ready,
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
    myShots: result?.myShots ?? 0,
    opponentShots: result?.opponentShots ?? 0,
    myFouls: result?.myFouls ?? 0,
    opponentFouls: result?.opponentFouls ?? 0,
  };
}
