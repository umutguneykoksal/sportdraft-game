import type { CompetitionMatchResponse, CompetitionResponse, LeaguePhase } from "../../shared/api/contracts";
import { formatMatchScore, resolveCompetitionStep, type CompetitionFlowStep } from "../competition/competitionFlow";

export function matchdayFromPhase(phase?: LeaguePhase | null): number {
  switch (phase) {
    case "MATCHDAY_1":
    case "BREAK_AFTER_MD1":
      return 1;
    case "MATCHDAY_2":
    case "BREAK_AFTER_MD2":
      return 2;
    case "MATCHDAY_3":
      return 3;
    default:
      return 0;
  }
}

export function matchesForMatchday(matches: CompetitionMatchResponse[], matchday: number) {
  return matches.filter((m) => m.roundKey.startsWith(`MD${matchday}_`));
}

export function findMyMatchdayMatch(
  matches: CompetitionMatchResponse[],
  leaguePhase: LeaguePhase | null | undefined,
  myUserId: string | null
): CompetitionMatchResponse | undefined {
  if (!myUserId) return undefined;
  const md = matchdayFromPhase(leaguePhase);
  if (md <= 0) return undefined;
  return matchesForMatchday(matches, md).find(
    (m) => m.homeUserId === myUserId || m.awayUserId === myUserId
  );
}

export function findPlayoffFinal(matches: CompetitionMatchResponse[]): CompetitionMatchResponse | undefined {
  return matches.find((m) => m.roundKey === "PLAYOFF_FINAL");
}

export type LeagueStep =
  | { kind: "watch"; matchId: string; replay: boolean }
  | { kind: "break"; endsAt: string }
  | { kind: "waiting"; phase: string }
  | { kind: "hub" };

export function resolveLeagueStep(
  competition: CompetitionResponse,
  myUserId: string | null
): LeagueStep {
  const phase = competition.leaguePhase;

  if (phase === "CHAMPIONSHIP_PLAYOFF") {
    const playoff = findPlayoffFinal(competition.matches);
    if (playoff?.playable) {
      return { kind: "watch", matchId: playoff.matchId, replay: false };
    }
    if (playoff?.watchedByMe) {
      return { kind: "waiting", phase: "CHAMPIONSHIP_PLAYOFF" };
    }
  }

  const step = resolveCompetitionStep(competition, {
    pausePhases: ["BREAK_AFTER_MD1", "BREAK_AFTER_MD2"],
    replayPhase: "REPLAYS",
    phase,
    myUserId,
    resolveActiveMatch: (matches, activePhase, userId) =>
      findMyMatchdayMatch(matches, activePhase as LeaguePhase | null | undefined, userId),
  });

  if (step.kind === "pause") {
    return { kind: "break", endsAt: step.endsAt };
  }
  return step;
}

export function formatLeagueMatchScore(match: CompetitionMatchResponse): string {
  return formatMatchScore(match, { penaltyRoundKey: "PLAYOFF_FINAL" });
}

export function matchdayLabel(roundKey: string): string {
  if (roundKey === "PLAYOFF_FINAL") return "Championship decider";
  return roundKey.replace("_", " ");
}
