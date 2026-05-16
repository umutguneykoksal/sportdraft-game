import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getMatchResult } from "../../../shared/api";
import { useSessionStream } from "../../../shared/sse";
import type { AuctionTeamCardResponse, Formation, MatchResultResponse } from "../../../shared/api/contracts";

export function useMatchFlow(sessionId: string | undefined) {
  const { events } = useSessionStream(sessionId ?? null);
  const [result, setResult] = useState<MatchResultResponse | null>(null);
  const loaded = useRef(false);

  const fetchResult = useCallback(async () => {
    if (!sessionId || loaded.current) return;
    try {
      const res = await getMatchResult(sessionId);
      if (res) {
        loaded.current = true;
        setResult(res);
      }
    } catch {
      // transient
    }
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

  const ready = useMemo(
    () => result !== null,
    [result],
  );

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
    myTeamId: result?.myTeamId ?? null,
    myShots: result?.myShots ?? 0,
    opponentShots: result?.opponentShots ?? 0,
    myFouls: result?.myFouls ?? 0,
    opponentFouls: result?.opponentFouls ?? 0,
  };
}
