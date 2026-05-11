import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getMatchResult } from "../../../shared/api";
import { useSessionStream } from "../../../shared/sse";

export function useMatchFlow(sessionId: string | undefined) {
  const { events } = useSessionStream(sessionId ?? null);
  const [homeScore, setHomeScore] = useState<number | null>(null);
  const [awayScore, setAwayScore] = useState<number | null>(null);
  const loaded = useRef(false);

  const fetchResult = useCallback(async () => {
    if (!sessionId || loaded.current) return;
    try {
      const result = await getMatchResult(sessionId);
      if (result) {
        loaded.current = true;
        setHomeScore(result.homeScore);
        setAwayScore(result.awayScore);
      }
    } catch {
      // transient
    }
  }, [sessionId]);

  // Initial load on mount
  useEffect(() => {
    fetchResult();
  }, [fetchResult]);

  // SSE match_completed triggers a single perspective-corrected REST fetch
  const matchCompletedReceived = useMemo(
    () => events.some((e) => e.eventType === "match_completed"),
    [events],
  );

  useEffect(() => {
    if (matchCompletedReceived) fetchResult();
  }, [matchCompletedReceived, fetchResult]);

  const ready = useMemo(
    () => typeof homeScore === "number" && typeof awayScore === "number",
    [homeScore, awayScore],
  );

  return { ready, homeScore, awayScore };
}
