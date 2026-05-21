import { useNavigate, useParams } from "react-router-dom";
import { CompetitionMatchResponse } from "../../shared/api";
import { clearActiveSessionId } from "../../shared/session";
import { useCompetitionHub } from "../competition/useCompetitionHub";
import {
  findFinal,
  findMySemi,
  formatMatchScore,
  resolveTournamentStep,
} from "./tournamentFlow";

const TOURNAMENT_SSE_EVENTS = [
  "tournament_updated",
  "tournament_completed",
  "tournament_intermission",
  "tournament_final_start",
];

function IntermissionScreen({
  competition,
  secondsLeft,
}: {
  competition: { matches: CompetitionMatchResponse[] };
  secondsLeft: number;
}) {
  const semis = competition.matches.filter((m) => m.roundKey.startsWith("SEMI"));

  return (
    <div className="fixed inset-0 z-50 bg-surface flex flex-col items-center justify-center px-4">
      <div className="w-full max-w-md space-y-6 text-center">
        <p className="text-sm uppercase tracking-widest text-amber-400">Semi-final results</p>
        <div className="inline-flex items-center justify-center w-24 h-24 rounded-full border-4 border-amber-400 bg-surface-card">
          <span className="text-4xl font-black text-white">{Math.max(0, secondsLeft)}</span>
        </div>
        <p className="text-slate-300">Final starts in {Math.max(0, secondsLeft)}s</p>
        <div className="space-y-3">
          {semis.map((semi) => (
            <div key={semi.matchId} className="rounded-xl border border-white/10 bg-surface-card p-4">
              <p className="text-xs text-slate-500 uppercase mb-2">{semi.roundKey.replace("_", "-")}</p>
              <div className="flex items-center justify-between gap-2 text-white font-medium">
                <span className="truncate">{semi.homeNickname}</span>
                <span className="text-card-gold shrink-0">{formatMatchScore(semi)}</span>
                <span className="truncate">{semi.awayNickname}</span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

function MatchRow({
  match,
  title,
  onWatch,
  watchLabel,
}: {
  match: CompetitionMatchResponse;
  title: string;
  onWatch?: () => void;
  watchLabel?: string;
}) {
  return (
    <div className="rounded-xl border border-white/10 bg-surface-card p-4">
      <p className="text-xs text-slate-500 uppercase tracking-wide mb-2">{title}</p>
      <div className="flex items-center justify-between gap-2 text-white font-medium">
        <span className="truncate">{match.homeNickname}</span>
        <span className="text-slate-400 text-sm shrink-0">{formatMatchScore(match)}</span>
        <span className="truncate text-right">{match.awayNickname}</span>
      </div>
      {onWatch && watchLabel && (
        <button
          type="button"
          onClick={onWatch}
          className="mt-3 w-full py-2 rounded-lg text-sm font-semibold bg-slate-700/60 border border-slate-500/40 text-slate-200 hover:bg-slate-700 transition-colors"
        >
          {watchLabel}
        </button>
      )}
    </div>
  );
}

export function TournamentBracketPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const { competition, error, loading, secondsLeft, myUserId } = useCompetitionHub(sessionId, {
    sseEventTypes: TOURNAMENT_SSE_EVENTS,
    resolveStep: resolveTournamentStep,
    matchPath: (sid, matchId) => `/tournament/${sid}/match/${matchId}`,
  });

  const isChampion =
    competition?.championUserId != null && competition.championUserId === myUserId;

  const mySemi = competition ? findMySemi(competition.matches, myUserId) : undefined;
  const finalMatch = competition ? findFinal(competition.matches) : undefined;
  const otherSemi = competition?.matches.find(
    (m) => m.roundKey.startsWith("SEMI") && m.matchId !== mySemi?.matchId
  );

  const watchReplay = (matchId: string) => {
    navigate(`/tournament/${sessionId}/match/${matchId}?replay=1`);
  };

  if (competition?.tournamentPhase === "INTERMISSION") {
    return <IntermissionScreen competition={competition} secondsLeft={secondsLeft} />;
  }

  return (
    <div className="min-h-screen bg-surface flex flex-col items-center px-4 py-8">
      <div className="w-full max-w-lg space-y-6">
        <div className="text-center">
          <h1 className="text-3xl font-extrabold text-white">Tournament</h1>
          <p className="text-slate-400 mt-1 text-sm">
            {competition?.replaysUnlocked
              ? "Replays unlocked — watch any match again"
              : competition?.tournamentPhase === "SEMIS"
                ? "Watch your semi-final"
                : competition?.tournamentPhase === "FINAL"
                  ? "Final — everyone watches together"
                  : "Tournament in progress"}
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
            ) : null}

            {competition.tournamentPhase === "SEMIS" && mySemi?.watchedByMe && (
              <div className="bg-surface-card border border-white/10 rounded-xl px-4 py-3 text-center">
                <p className="text-slate-400 text-sm">Waiting for other semi-finals to finish…</p>
              </div>
            )}

            {competition.tournamentPhase === "FINAL" && finalMatch?.watchedByMe && !competition.replaysUnlocked && (
              <div className="bg-surface-card border border-white/10 rounded-xl px-4 py-3 text-center">
                <p className="text-slate-400 text-sm">Waiting for others to finish the final…</p>
              </div>
            )}

            {competition.replaysUnlocked ? (
              <div className="space-y-3">
                <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wide">Replays</h2>
                {competition.matches.map((m) => (
                  <MatchRow
                    key={m.matchId}
                    match={m}
                    title={m.roundKey.replace("_", " ")}
                    onWatch={() => watchReplay(m.matchId)}
                    watchLabel="Watch again"
                  />
                ))}
              </div>
            ) : (
              <>
                {mySemi && (
                  <div className="space-y-3">
                    <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wide">Your semi-final</h2>
                    <MatchRow match={mySemi} title="Your match" />
                  </div>
                )}
                {otherSemi && (
                  <div className="space-y-3">
                    <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wide">Other semi-final</h2>
                    <MatchRow match={otherSemi} title="Other match" />
                  </div>
                )}
                {finalMatch && (
                  <div className="space-y-3">
                    <h2 className="text-sm font-semibold text-slate-400 uppercase tracking-wide">Final</h2>
                    <MatchRow match={finalMatch} title="Final" />
                  </div>
                )}
              </>
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
