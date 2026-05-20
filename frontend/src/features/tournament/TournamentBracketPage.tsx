import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  CompetitionMatchResponse,
  CompetitionResponse,
  getClientUserId,
  getCompetitionBySession,
} from "../../shared/api";
import { clearActiveSessionId } from "../../shared/session";

function MatchCard({ match }: { match: CompetitionMatchResponse }) {
  const done = match.status === "COMPLETED";
  const score =
    match.homeScore != null && match.awayScore != null
      ? `${match.homeScore} – ${match.awayScore}`
      : "—";

  return (
    <div
      className={`rounded-xl border p-4 ${
        done ? "border-accent-green/40 bg-accent-green/5" : "border-white/10 bg-surface-card"
      }`}
    >
      <p className="text-xs text-slate-500 uppercase tracking-wide mb-2">{match.roundKey.replace("_", " ")}</p>
      <div className="flex items-center justify-between gap-2 text-white font-medium">
        <span className="truncate">{match.homeNickname}</span>
        <span className="text-slate-400 text-sm shrink-0">{score}</span>
        <span className="truncate text-right">{match.awayNickname}</span>
      </div>
    </div>
  );
}

export function TournamentBracketPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const myUserId = getClientUserId();
  const [competition, setCompetition] = useState<CompetitionResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!sessionId) return;
    let cancelled = false;
    (async () => {
      try {
        const data = await getCompetitionBySession(sessionId);
        if (!cancelled) setCompetition(data);
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : "Failed to load bracket");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [sessionId]);

  const isChampion =
    competition?.championUserId != null && competition.championUserId === myUserId;

  const semis = competition?.matches.filter((m) => m.roundKey.startsWith("SEMI")) ?? [];
  const finalMatch = competition?.matches.find((m) => m.roundKey === "FINAL");

  return (
    <div className="min-h-screen bg-surface flex flex-col items-center px-4 py-8">
      <div className="w-full max-w-lg space-y-6">
        <div className="text-center">
          <h1 className="text-3xl font-extrabold text-white">Tournament</h1>
          <p className="text-slate-400 mt-1 text-sm">Draft complete — bracket results</p>
        </div>

        {loading && <p className="text-center text-slate-400">Loading bracket...</p>}
        {error && (
          <div className="bg-red-900/30 border border-red-500/30 text-red-400 px-4 py-3 rounded-lg">
            {error}
          </div>
        )}

        {competition && (
          <>
            {competition.championNickname && (
              <div className="bg-accent-green/10 border border-accent-green/30 rounded-xl p-6 text-center">
                <p className="text-sm text-accent-green">Champion</p>
                <p className="text-2xl font-bold text-white mt-1">{competition.championNickname}</p>
                {isChampion && (
                  <p className="text-accent-green text-sm mt-2">You won the tournament!</p>
                )}
              </div>
            )}

            <div className="space-y-3">
              <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wide">Semi-finals</h2>
              {semis.map((m) => (
                <MatchCard key={m.matchId} match={m} />
              ))}
            </div>

            {finalMatch && (
              <div className="space-y-3">
                <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wide">Final</h2>
                <MatchCard match={finalMatch} />
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
