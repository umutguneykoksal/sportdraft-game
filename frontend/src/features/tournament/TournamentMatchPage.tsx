import { useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
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

export function TournamentMatchPage() {
  const { sessionId, matchId } = useParams<{ sessionId: string; matchId: string }>();
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
        const data = await openTournamentMatch(sessionId, matchId);
        if (!cancelled) setPayload(data);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : "Failed to load match");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [sessionId, matchId]);

  const derivedScores = useMemo(() => {
    if (!payload) return { home: 0, away: 0 };
    return scoresFromTimeline(payload.timeline, payload.homeUserId);
  }, [payload]);

  const handleComplete = async () => {
    if (!sessionId || !matchId || ackInFlight) return;
    setAckInFlight(true);
    try {
      await ackTournamentMatchWatch(sessionId, matchId);
      navigate(`/tournament/${sessionId}`);
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
          onClick={() => navigate(sessionId ? `/tournament/${sessionId}` : "/lobby")}
          className="text-accent hover:underline"
        >
          Back to bracket
        </button>
      </div>
    );
  }

  const label = payload.roundKey.replace("_", " ");

  return (
    <div className="min-h-screen bg-surface flex flex-col">
      <div className="bg-surface-card border-b border-white/10 px-4 py-4 text-center">
        <h1 className="text-2xl font-extrabold text-white">{label}</h1>
        <p className="text-slate-400 text-sm mt-1">
          {payload.homeNickname} vs {payload.awayNickname}
        </p>
        <p className="text-xs text-slate-500 mt-1">
          Watch the full simulation — bracket updates after everyone finishes
        </p>
      </div>

      {payload.timeline.length > 0 ? (
        <MatchCommentary
          timeline={payload.timeline}
          homeTeamId={payload.homeUserId}
          onComplete={handleComplete}
          finalHomeScore={derivedScores.home}
          finalAwayScore={derivedScores.away}
          homeNickname={payload.homeNickname}
          awayNickname={payload.awayNickname}
        />
      ) : (
        <div className="flex-1 flex flex-col items-center justify-center gap-4 px-4">
          <p className="text-slate-300">Simulation complete.</p>
          <button
            type="button"
            disabled={ackInFlight}
            onClick={handleComplete}
            className="bg-accent hover:bg-blue-600 disabled:opacity-40 text-white font-bold px-6 py-3 rounded-lg"
          >
            {ackInFlight ? "Saving…" : "Continue to bracket"}
          </button>
        </div>
      )}
    </div>
  );
}
