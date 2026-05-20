import type { CompetitionMatchResponse, CompetitionResponse, TournamentPhase } from "../../shared/api/contracts";

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
  | { kind: "waiting"; phase: TournamentPhase }
  | { kind: "hub" };

export function resolveTournamentStep(
  competition: CompetitionResponse,
  myUserId: string | null
): TournamentStep {
  const { tournamentPhase, matches, intermissionEndsAt } = competition;

  if (tournamentPhase === "INTERMISSION" && intermissionEndsAt) {
    return { kind: "intermission", endsAt: intermissionEndsAt };
  }

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

  if (tournamentPhase === "REPLAYS") {
    return { kind: "hub" };
  }

  return { kind: "hub" };
}

export function formatMatchScore(match: CompetitionMatchResponse): string {
  if (match.homeScore == null || match.awayScore == null) {
    return "—";
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
