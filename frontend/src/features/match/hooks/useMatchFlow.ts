import { useEffect, useMemo, useState } from "react";
import { getMatchResult } from "../../../shared/api";
import { useSessionStream } from "../../../shared/sse";

export function useMatchFlow(sessionId: string | undefined) {
  const { events } = useSessionStream(sessionId ?? null);
  const [homeScore, setHomeScore] = useState<number | null>(null);
  const [awayScore, setAwayScore] = useState<number | null>(null);

  useEffect(() => {
    const latest = events
      .slice()
      .reverse()
      .find((event) => event.eventType === "match_completed");
    if (!latest) {
      return;
    }
    const payload = latest.payload as { homeScore?: number; awayScore?: number };
    if (typeof payload.homeScore === "number") {
      setHomeScore(payload.homeScore);
    }
    if (typeof payload.awayScore === "number") {
      setAwayScore(payload.awayScore);
    }
  }, [events]);

  useEffect(() => {
    if (!sessionId || (homeScore !== null && awayScore !== null)) {
      return;
    }
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
  }, [sessionId, homeScore, awayScore]);

  const ready = useMemo(
    () => typeof homeScore === "number" && typeof awayScore === "number",
    [homeScore, awayScore]
  );

  return { ready, homeScore, awayScore };
}
