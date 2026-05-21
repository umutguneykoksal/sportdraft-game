import type { CompetitionMatchResponse, CompetitionResponse, TournamentPhase } from "../../shared/api/contracts";
import { formatMatchScore, resolveCompetitionStep, type CompetitionFlowStep } from "../competition/competitionFlow";

export function findMySemi(
  matches: CompetitionMatchResponse[],
  myUserId: string | null
): CompetitionMatchResponse | undefined {
  if (!myUserId) return undefined;
  return matches.find(
    (m) =>
      m.roundKey.startsWith("SEMI")
      && (m.homeUserId === myUserId || m.awayUserId === myUserId)
  );
}

export function findFinal(matches: CompetitionMatchResponse[]): CompetitionMatchResponse | undefined {
  return matches.find((m) => m.roundKey === "FINAL");
}

export type TournamentStep =
  | { kind: "watch"; matchId: string; replay: boolean }
  | { kind: "intermission"; endsAt: string }
  | { kind: "waiting"; phase: string }
  | { kind: "hub" };

export function resolveTournamentStep(
  competition: CompetitionResponse,
  myUserId: string | null
): TournamentStep {
  const { tournamentPhase, matches } = competition;

  if (tournamentPhase === "SEMIS") {
    const mySemi = findMySemi(matches, myUserId);
    if (mySemi?.playable) {
      return { kind: "watch", matchId: mySemi.matchId, replay: false };
    }
    if (mySemi?.watchedByMe) {
      return { kind: "waiting", phase: "SEMIS" };
    }
  }

  if (tournamentPhase === "FINAL") {
    const finalMatch = findFinal(matches);
    if (finalMatch?.playable) {
      return { kind: "watch", matchId: finalMatch.matchId, replay: false };
    }
    if (finalMatch?.watchedByMe) {
      return { kind: "waiting", phase: "FINAL" };
    }
  }

  const step = resolveCompetitionStep(competition, {
    pausePhases: ["INTERMISSION"],
    replayPhase: "REPLAYS",
    phase: tournamentPhase,
    myUserId,
    resolveActiveMatch: () => undefined,
  });

  if (step.kind === "pause") {
    return { kind: "intermission", endsAt: step.endsAt };
  }
  return step;
}

export { formatMatchScore };
