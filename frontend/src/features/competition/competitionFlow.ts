import type { CompetitionMatchResponse } from "../../shared/api/contracts";

export function formatMatchScore(
  match: CompetitionMatchResponse,
  options?: { penaltyRoundKey?: string }
): string {
  if (match.homeScore == null || match.awayScore == null) {
    return "—";
  }
  const base = `${match.homeScore} – ${match.awayScore}`;
  const penaltyRound = options?.penaltyRoundKey;
  const showPenalties =
    match.homePenaltyScore != null
    && match.awayPenaltyScore != null
    && match.homeScore === match.awayScore
    && (penaltyRound == null || match.roundKey === penaltyRound);
  if (showPenalties) {
    return `${base} (${match.homePenaltyScore}–${match.awayPenaltyScore} pens)`;
  }
  return base;
}

export type CompetitionFlowStep =
  | { kind: "watch"; matchId: string; replay: boolean }
  | { kind: "pause"; endsAt: string }
  | { kind: "waiting"; phase: string }
  | { kind: "hub" };

export function resolveCompetitionStep(
  competition: {
    intermissionEndsAt?: string | null;
    matches: CompetitionMatchResponse[];
  },
  config: {
    pausePhases: string[];
    replayPhase: string;
    resolveActiveMatch: (
      matches: CompetitionMatchResponse[],
      phase: string | null | undefined,
      myUserId: string | null
    ) => CompetitionMatchResponse | undefined;
    phase: string | null | undefined;
    myUserId: string | null;
  }
): CompetitionFlowStep {
  const { intermissionEndsAt, matches } = competition;
  const { pausePhases, replayPhase, resolveActiveMatch, phase, myUserId } = config;

  if (phase && pausePhases.includes(phase) && intermissionEndsAt) {
    return { kind: "pause", endsAt: intermissionEndsAt };
  }

  if (phase && phase !== replayPhase) {
    const activeMatch = resolveActiveMatch(matches, phase, myUserId);
    if (activeMatch?.playable) {
      return { kind: "watch", matchId: activeMatch.matchId, replay: false };
    }
    if (activeMatch?.watchedByMe) {
      return { kind: "waiting", phase };
    }
  }

  if (phase === replayPhase) {
    return { kind: "hub" };
  }

  return { kind: "hub" };
}
