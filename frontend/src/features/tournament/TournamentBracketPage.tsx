import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  CompetitionMatchResponse,
  CompetitionResponse,
  getClientUserId,
  getCompetitionBySession,
} from "../../shared/api";
import { clearActiveSessionId } from "../../shared/session";
import { useSessionStream } from "../../shared/sse";

function roundLabel(roundKey: string): string {
  if (roundKey === "SEMI_1" || roundKey === "SEMI_2") return roundKey.replace("_", "-");
  return roundKey;
}

function formatScore(match: CompetitionMatchResponse): string {
  if (match.homeScore == null || match.awayScore == null) {
    return match.status === "COMPLETED" ? "Watch to reveal score" : "—";
  }
  const base = `${match.homeScore} – ${match.awayScore}`;
  if (
    match.homePenaltyScore != null &&
    match.awayPenaltyScore != null &&
    match.homeScore === match.awayScore
  ) {
    return `${base} (${match.homePenaltyScore}–${match.awayPenaltyScore} pens)`;
  }
  return base;
}

function MatchCard({
  match,
  onWatch,
}: {
  match: CompetitionMatchResponse;
  onWatch: (matchId: string) => void;
}) {
  const done = match.status === "COMPLETED";
  const locked = !match.playable && match.status === "PENDING";
  const score = formatScore(match);

  return (
    <div
      className={`rounded-xl border p-4 ${
        match.watchedByMe
          ? "border-accent-green/40 bg-accent-green/5"
          : locked
            ? "border-slate-600/40 bg-slate-900/40 opacity-70"
            : "border-white/10 bg-surface-card"
      }`}
    >
      <div className="flex items-center justify-between gap-2 mb-2">
        <p className="text-xs text-slate-500 uppercase tracking-wide">{roundLabel(match.roundKey)}</p>
        <p className="text-xs text-slate-500">
          {match.watchCount}/{match.requiredWatchers} watched
        </p>
      </div>
      <div className="flex items-center justify-between gap-2 text-white font-medium">
        <span className="truncate">{match.homeNickname}</span>
        <span className="text-slate-400 text-sm shrink-0">{score}</span>
        <span className="truncate text-right">{match.awayNickname}</span>
      </div>
      {locked && (
        <p className="text-xs text-amber-400/90 mt-2">
          Locked until all players watch both semi-finals
        </p>
      )}
      {match.playable && (
        <button
          type="button"
          onClick={() => onWatch(match.matchId)}
          className={`mt-3 w-full py-2 rounded-lg text-sm font-semibold transition-colors ${
            match.watchedByMe
              ? "bg-slate-700/60 border border-slate-500/40 text-slate-200 hover:bg-slate-700"
              : "bg-accent hover:bg-blue-600 text-white"
          }`}
        >
          {match.watchedByMe ? "Watch again" : "Watch match"}
        </button>
      )}
      {done && match.allPlayersWatched && (
        <p className="text-xs text-accent-green mt-2 text-center">All players watched</p>
      )}
    </div>
  );
}

export function TournamentBracketPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const myUserId = getClientUserId();
  const { events } = useSessionStream(sessionId ?? null);
  const [competition, setCompetition] = useState<CompetitionResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    if (!sessionId) return;
    try {
      const data = await getCompetitionBySession(sessionId);
      setCompetition(data);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load bracket");
    } finally {
      setLoading(false);
    }
  }, [sessionId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  useEffect(() => {
    const relevant = events.some(
      (e) => e.eventType === "tournament_updated" || e.eventType === "tournament_completed"
    );
    if (relevant) refresh();
  }, [events, refresh]);

  const isChampion =
    competition?.championUserId != null && competition.championUserId === myUserId;

  const semis = competition?.matches.filter((m) => m.roundKey.startsWith("SEMI")) ?? [];
  const finalMatch = competition?.matches.find((m) => m.roundKey === "FINAL");

  const pendingWatch = competition?.matches.some((m) => m.playable && !m.watchedByMe) ?? false;

  return (
    <div className="min-h-screen bg-surface flex flex-col items-center px-4 py-8">
      <div className="w-full max-w-lg space-y-6">
        <div className="text-center">
          <h1 className="text-3xl font-extrabold text-white">Tournament</h1>
          <p className="text-slate-400 mt-1 text-sm">
            Watch every match — scores stay hidden until you do
          </p>
        </div>

        {loading && <p className="text-center text-slate-400">Loading bracket...</p>}
        {error && (
          <div className="bg-red-900/30 border border-red-500/30 text-red-400 px-4 py-3 rounded-lg">
            {error}
          </div>
        )}

        {competition && (
          <>
            {competition.championNickname ? (
              <div className="bg-accent-green/10 border border-accent-green/30 rounded-xl p-6 text-center">
                <p className="text-sm text-accent-green">Champion</p>
                <p className="text-2xl font-bold text-white mt-1">{competition.championNickname}</p>
                {isChampion && (
                  <p className="text-accent-green text-sm mt-2">You won the tournament!</p>
                )}
              </div>
            ) : pendingWatch ? (
              <div className="bg-amber-900/20 border border-amber-500/30 rounded-xl px-4 py-3 text-center">
                <p className="text-amber-300 text-sm">
                  Watch all available matches to unlock the final and crown the champion
                </p>
              </div>
            ) : null}

            <div className="space-y-3">
              <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wide">Semi-finals</h2>
              {semis.map((m) => (
                <MatchCard
                  key={m.matchId}
                  match={m}
                  onWatch={(id) => navigate(`/tournament/${sessionId}/match/${id}`)}
                />
              ))}
            </div>

            {finalMatch && (
              <div className="space-y-3">
                <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wide">Final</h2>
                <MatchCard
                  match={finalMatch}
                  onWatch={(id) => navigate(`/tournament/${sessionId}/match/${id}`)}
                />
              </div>
            )}
          </>
        )}

        <button
          type="button"
          onClick={() => {
            clearActiveSessionId();
            navigate("/lobby");
          }}
          className="w-full bg-surface-card border border-white/10 hover:border-white/20 text-white font-semibold py-3 rounded-xl transition-colors"
        >
          Back to lobby
        </button>
      </div>
    </div>
  );
}
