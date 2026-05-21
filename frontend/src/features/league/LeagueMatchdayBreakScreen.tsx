import type { CompetitionMatchResponse, CompetitionResponse } from "../../shared/api/contracts";
import { formatLeagueMatchScore, matchdayFromPhase, matchesForMatchday } from "./leagueFlow";
import { LeagueStandingsTable } from "./LeagueStandingsTable";

export function LeagueMatchdayBreakScreen({
  competition,
  secondsLeft,
}: {
  competition: CompetitionResponse;
  secondsLeft: number;
}) {
  const completedMd = competition.leaguePhase === "BREAK_AFTER_MD1" ? 1 : 2;
  const dayMatches = matchesForMatchday(competition.matches, completedMd);

  return (
    <div className="fixed inset-0 z-50 bg-surface flex flex-col items-center justify-center px-4">
      <div className="w-full max-w-3xl space-y-6">
        <div className="text-center">
          <p className="text-sm uppercase tracking-widest text-amber-400">Matchday break</p>
          <div className="inline-flex items-center justify-center w-24 h-24 rounded-full border-4 border-amber-400 bg-surface-card mt-4">
            <span className="text-4xl font-black text-white">{Math.max(0, secondsLeft)}</span>
          </div>
          <p className="text-slate-300 mt-3">Next matchday starts in {Math.max(0, secondsLeft)}s</p>
        </div>
        <div className="grid lg:grid-cols-2 gap-4">
          <div className="space-y-3">
            <p className="text-xs text-slate-500 uppercase tracking-wide">Results</p>
            {dayMatches.map((m) => (
              <MatchRow key={m.matchId} match={m} />
            ))}
          </div>
          <div className="rounded-xl border border-white/10 bg-surface-card p-4">
            <p className="text-xs text-slate-500 uppercase tracking-wide mb-3">Standings</p>
            <LeagueStandingsTable standings={competition.standings ?? []} />
          </div>
        </div>
      </div>
    </div>
  );
}

function MatchRow({ match }: { match: CompetitionMatchResponse }) {
  return (
    <div className="rounded-xl border border-white/10 bg-surface-card p-4">
      <p className="text-xs text-slate-500 uppercase mb-2">{match.roundKey.replace("_", " ")}</p>
      <div className="flex items-center justify-between gap-2 text-white font-medium">
        <span className="truncate">{match.homeNickname}</span>
        <span className="text-card-gold shrink-0">{formatLeagueMatchScore(match)}</span>
        <span className="truncate text-right">{match.awayNickname}</span>
      </div>
    </div>
  );
}
