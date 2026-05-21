import { useNavigate, useParams } from "react-router-dom";
import {
  CompetitionMatchResponse,
} from "../../shared/api";
import { clearActiveSessionId } from "../../shared/session";
import { useCompetitionHub } from "../competition/useCompetitionHub";
import { LeagueMatchdayBreakScreen } from "./LeagueMatchdayBreakScreen";
import { LeagueStandingsTable } from "./LeagueStandingsTable";
import {
  findMyMatchdayMatch,
  findPlayoffFinal,
  formatLeagueMatchScore,
  matchdayFromPhase,
  matchdayLabel,
  matchesForMatchday,
  resolveLeagueStep,
} from "./leagueFlow";

const LEAGUE_SSE_EVENTS = [
  "league_started",
  "league_updated",
  "league_completed",
  "league_matchday_break",
  "league_matchday_started",
  "league_playoff_started",
];

function MatchCard({
  match,
  onWatch,
  watchLabel,
}: {
  match: CompetitionMatchResponse;
  onWatch?: () => void;
  watchLabel?: string;
}) {
  return (
    <div className="rounded-xl border border-white/10 bg-surface-card p-4">
      <p className="text-xs text-slate-500 uppercase tracking-wide mb-2">{matchdayLabel(match.roundKey)}</p>
      <div className="flex items-center justify-between gap-2 text-white font-medium">
        <span className="truncate">{match.homeNickname}</span>
        <span className="text-slate-400 text-sm shrink-0">{formatLeagueMatchScore(match)}</span>
        <span className="truncate text-right">{match.awayNickname}</span>
      </div>
      {onWatch && watchLabel && (
        <button
          type="button"
          onClick={onWatch}
          className="mt-3 w-full py-2 rounded-lg text-sm font-semibold bg-accent hover:bg-blue-600 text-white transition-colors"
        >
          {watchLabel}
        </button>
      )}
    </div>
  );
}

export function LeagueTablePage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const { competition, error, loading, secondsLeft, myUserId } = useCompetitionHub(sessionId, {
    sseEventTypes: LEAGUE_SSE_EVENTS,
    resolveStep: resolveLeagueStep,
    matchPath: (sid, matchId) => `/league/${sid}/match/${matchId}`,
  });

  const phase = competition?.leaguePhase;
  const md = matchdayFromPhase(phase);
  const myMatch = competition ? findMyMatchdayMatch(competition.matches, phase, myUserId) : undefined;
  const playoff = competition ? findPlayoffFinal(competition.matches) : undefined;
  const activeMatches = competition && md > 0 ? matchesForMatchday(competition.matches, md) : [];
  const coChamps = competition?.coChampionNicknames ?? [];
  const isCoChampion = coChamps.length > 0 && competition?.coChampionUserIds?.includes(myUserId ?? "");
  const isChampion = competition?.championUserId === myUserId || isCoChampion;

  if (phase === "BREAK_AFTER_MD1" || phase === "BREAK_AFTER_MD2") {
    return competition ? (
      <LeagueMatchdayBreakScreen competition={competition} secondsLeft={secondsLeft} />
    ) : null;
  }

  const phaseSubtitle = (() => {
    if (competition?.replaysUnlocked) return "Replays unlocked";
    if (phase === "CHAMPIONSHIP_PLAYOFF") return "Championship decider";
    if (md > 0) return `Matchday ${md} — watch your fixture`;
    return "League in progress";
  })();

  return (
    <div className="min-h-screen bg-surface flex flex-col items-center px-4 py-8">
      <div className="w-full max-w-5xl space-y-6">
        <div className="text-center">
          <h1 className="text-3xl font-extrabold text-white">League</h1>
          <p className="text-slate-400 mt-1 text-sm">{phaseSubtitle}</p>
        </div>

        {loading && <p className="text-center text-slate-400">Loading league...</p>}
        {error && (
          <div className="bg-red-900/30 border border-red-500/30 text-red-400 px-4 py-3 rounded-lg">{error}</div>
        )}

        {competition && (
          <div className="grid lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 space-y-4">
              {competition.championNickname && (
                <div className="rounded-xl border border-card-gold/30 bg-card-gold/10 p-4 text-center">
                  <p className="text-xs uppercase tracking-widest text-card-gold">Champion</p>
                  <p className="text-2xl font-bold text-white mt-1">{competition.championNickname}</p>
                  {isChampion && <p className="text-emerald-400 text-sm mt-2">You won the league!</p>}
                </div>
              )}
              {coChamps.length > 0 && (
                <div className="rounded-xl border border-card-gold/30 bg-card-gold/10 p-4 text-center">
                  <p className="text-xs uppercase tracking-widest text-card-gold">Joint champions</p>
                  <p className="text-xl font-bold text-white mt-1">{coChamps.join(" · ")}</p>
                </div>
              )}

              {phase === "CHAMPIONSHIP_PLAYOFF" && playoff && (
                <MatchCard
                  match={playoff}
                  onWatch={playoff.playable ? () => navigate(`/league/${sessionId}/match/${playoff.matchId}`) : undefined}
                  watchLabel={playoff.playable ? "Watch decider" : undefined}
                />
              )}

              {md > 0 && (
                <>
                  <p className="text-sm text-slate-400 uppercase tracking-wide">Matchday {md}</p>
                  {activeMatches.map((m) => (
                    <MatchCard
                      key={m.matchId}
                      match={m}
                      onWatch={
                        m.playable && m.matchId === myMatch?.matchId
                          ? () => navigate(`/league/${sessionId}/match/${m.matchId}`)
                          : undefined
                      }
                      watchLabel={m.playable && m.matchId === myMatch?.matchId ? "Watch your match" : undefined}
                    />
                  ))}
                  {myMatch?.watchedByMe && !competition.replaysUnlocked && (
                    <p className="text-slate-500 text-sm text-center">Waiting for other matches this matchday…</p>
                  )}
                </>
              )}

              {competition.replaysUnlocked && (
                <div className="space-y-3">
                  <p className="text-sm text-slate-400 uppercase tracking-wide">All matches</p>
                  {competition.matches.map((m) => (
                    <MatchCard
                      key={m.matchId}
                      match={m}
                      onWatch={() => navigate(`/league/${sessionId}/match/${m.matchId}?replay=1`)}
                      watchLabel="Replay"
                    />
                  ))}
                </div>
              )}
            </div>

            <div className="rounded-xl border border-white/10 bg-surface-card p-4 h-fit">
              <p className="text-sm text-slate-400 uppercase tracking-wide mb-3">Standings</p>
              <LeagueStandingsTable standings={competition.standings ?? []} />
            </div>
          </div>
        )}

        <button
          type="button"
          onClick={() => { clearActiveSessionId(); navigate("/lobby"); }}
          className="w-full max-w-xs mx-auto block text-slate-400 hover:text-white text-sm py-2"
        >
          Back to lobby
        </button>
      </div>
    </div>
  );
}
