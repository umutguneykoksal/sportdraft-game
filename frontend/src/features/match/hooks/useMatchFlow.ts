import { useEffect, useMemo, useState } from "react";
import { getMatchResult } from "../../../shared/api";
import { useSessionStream } from "../../../shared/sse";

export function useMatchFlow(sessionId: string | undefined) {
  const { events } = useSessionStream(sessionId ?? null);
  const [homeScore, setHomeScore] = useState<number | null>(null);
  const [awayScore, setAwayScore] = useState<number | null>(null);

  const matchCompletedReceived = useMemo(
    () => events.some((e) => e.eventType === "match_completed"),
    [events]
  );

  useEffect(() => {
    if (!sessionId) return;
    let cancelled = false;
    const load = async () => {
      try {
        const result = await getMatchResult(sessionId);
        if (!cancelled && result) {
          setHomeScore(result.homeScore);
          setAwayScore(result.awayScore);
        }
      } catch {
        // ignore
      }
    };
    load();
    const id = window.setInterval(load, 1500);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, [sessionId, matchCompletedReceived]);

  const ready = useMemo(
    () => typeof homeScore === "number" && typeof awayScore === "number",
    [homeScore, awayScore]
  );

  return { ready, homeScore, awayScore };
}
