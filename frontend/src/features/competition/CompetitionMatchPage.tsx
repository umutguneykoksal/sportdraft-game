import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  ackTournamentMatchWatch,
  openTournamentMatch,
  type CompetitionMatchOpenResponse,
  type MatchEvent,
} from "../../shared/api";
import { MatchCommentary } from "../match/components/MatchCommentary";

function scoresFromTimeline(timeline: MatchEvent[], homeUserId: string) {
  let home = 0;
  let away = 0;
  for (const event of timeline) {
    if (event.type !== "GOAL" || !event.teamId) continue;
    if (event.teamId === homeUserId) home += 1;
    else away += 1;
  }
  return { home, away };
}

type CompetitionMatchPageConfig = {
  hubPath: (sessionId: string) => string;
  fallbackPath: string;
  backLabel: string;
  formatRoundLabel?: (roundKey: string) => string;
};

type Props = {
  config: CompetitionMatchPageConfig;
};

export function CompetitionMatchPage({ config }: Props) {
  const { sessionId, matchId } = useParams<{ sessionId: string; matchId: string }>();
  const [searchParams] = useSearchParams();
  const isReplay = searchParams.get("replay") === "1";
  const navigate = useNavigate();
  const [payload, setPayload] = useState<CompetitionMatchOpenResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [ackInFlight, setAckInFlight] = useState(false);

  useEffect(() => {
    if (!sessionId || !matchId) return;
    let cancelled = false;
    (async () => {
      try {
        const data = await openTournamentMatch(sessionId, matchId, isReplay);
        if (!cancelled) setPayload(data);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : "Failed to load match");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [sessionId, matchId, isReplay]);

  const derivedScores = useCallback(() => {
    if (!payload) return { home: 0, away: 0 };
    if (payload.homeScore != null && payload.awayScore != null) {
      return { home: payload.homeScore, away: payload.awayScore };
    }
    return scoresFromTimeline(payload.timeline, payload.homeUserId);
  }, [payload]);

  const handleComplete = async () => {
    if (!sessionId || !matchId || ackInFlight) return;
    if (isReplay) {
      navigate(config.hubPath(sessionId));
      return;
    }
    setAckInFlight(true);
    try {
      await ackTournamentMatchWatch(sessionId, matchId);
      navigate(config.hubPath(sessionId));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to save watch progress");
      setAckInFlight(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-surface flex items-center justify-center text-slate-400">
        Preparing match simulation…
      </div>
    );
  }

  if (error || !payload) {
    return (
      <div className="min-h-screen bg-surface flex flex-col items-center justify-center px-4 gap-4">
        <p className="text-red-400">{error ?? "Match not found"}</p>
        <button
          type="button"
          onClick={() => navigate(sessionId ? config.hubPath(sessionId) : config.fallbackPath)}
          className="text-accent hover:underline"
        >
          {config.backLabel}
        </button>
      </div>
    );
  }

  const label = config.formatRoundLabel
    ? config.formatRoundLabel(payload.roundKey)
    : payload.roundKey.replace("_", " ");
  const scores = derivedScores();

  return (
    <div className="min-h-screen bg-surface flex flex-col">
      <div className="bg-surface-card border-b border-white/10 px-4 py-4 text-center">
        <h1 className="text-2xl font-extrabold text-white">
          {isReplay ? `${label} (replay)` : label}
        </h1>
        <p className="text-slate-400 text-sm mt-1">
          {payload.homeNickname} vs {payload.awayNickname}
        </p>
      </div>

      {payload.timeline.length > 0 ? (
        <MatchCommentary
          timeline={payload.timeline}
          homeTeamId={payload.homeUserId}
          onComplete={handleComplete}
          finalHomeScore={scores.home}
          finalAwayScore={scores.away}
          homeNickname={payload.homeNickname}
          awayNickname={payload.awayNickname}
        />
      ) : (
        <div className="flex-1 flex items-center justify-center text-slate-400">
          Match simulation in progress…
        </div>
      )}
    </div>
  );
}
