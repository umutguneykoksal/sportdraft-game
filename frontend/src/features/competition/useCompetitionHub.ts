import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  CompetitionResponse,
  getClientUserId,
  getCompetitionBySession,
} from "../../shared/api";
import { useSessionStream } from "../../shared/sse";

export type CompetitionHubConfig<TStep extends { kind: string }> = {
  sseEventTypes: string[];
  resolveStep: (competition: CompetitionResponse, myUserId: string | null) => TStep;
  matchPath: (sessionId: string, matchId: string) => string;
};

export function useCompetitionHub<TStep extends { kind: string; matchId?: string; replay?: boolean }>(
  sessionId: string | undefined,
  config: CompetitionHubConfig<TStep>
) {
  const navigate = useNavigate();
  const myUserId = getClientUserId();
  const { events } = useSessionStream(sessionId ?? null);
  const [competition, setCompetition] = useState<CompetitionResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [secondsLeft, setSecondsLeft] = useState(0);
  const autoNavigatedRef = useRef(false);

  const refresh = useCallback(async () => {
    if (!sessionId) return;
    const maxAttempts = 8;
    for (let attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        const data = await getCompetitionBySession(sessionId);
        setCompetition(data);
        setError(null);
        setLoading(false);
        return;
      } catch (e) {
        const message = e instanceof Error ? e.message : "Failed to load competition";
        const retryable = message.toLowerCase().includes("not found") && attempt < maxAttempts - 1;
        if (retryable) {
          await new Promise((resolve) => setTimeout(resolve, 500 * (attempt + 1)));
          continue;
        }
        setError(message);
        setLoading(false);
        return;
      }
    }
  }, [sessionId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    const relevant = events.some((e) => config.sseEventTypes.includes(e.eventType));
    if (relevant) {
      autoNavigatedRef.current = false;
      refresh();
    }
  }, [config.sseEventTypes, events, refresh]);

  useEffect(() => {
    if (!competition?.intermissionEndsAt) return;
    const tick = () => {
      const ms = Date.parse(competition.intermissionEndsAt!) - Date.now();
      setSecondsLeft(Math.max(0, Math.ceil(ms / 1000)));
    };
    tick();
    const id = window.setInterval(tick, 250);
    return () => window.clearInterval(id);
  }, [competition?.intermissionEndsAt]);

  useEffect(() => {
    if (!sessionId || !competition || loading) return;
    const step = config.resolveStep(competition, myUserId);
    if (step.kind === "watch" && !step.replay && step.matchId && !autoNavigatedRef.current) {
      autoNavigatedRef.current = true;
      navigate(config.matchPath(sessionId, step.matchId), { replace: true });
    }
  }, [competition, config, loading, myUserId, navigate, sessionId]);

  return {
    competition,
    error,
    loading,
    secondsLeft,
    myUserId,
    refresh,
  };
}
