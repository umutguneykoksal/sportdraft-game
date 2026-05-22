import {
  findMyMatchdayMatch,
  formatLeagueMatchScore,
  matchdayFromPhase,
  matchdayLabel,
  resolveLeagueStep,
} from "./leagueFlow";
import type { CompetitionMatchResponse, CompetitionResponse } from "../../shared/api/contracts";

const match = (overrides: Partial<CompetitionMatchResponse> = {}): CompetitionMatchResponse => ({
  matchId: "m1",
  roundKey: "MD1_A",
  homeUserId: "me",
  homeNickname: "Me",
  awayUserId: "other",
  awayNickname: "Other",
  homeScore: null,
  awayScore: null,
  homePenaltyScore: null,
  awayPenaltyScore: null,
  status: "PENDING",
  playable: true,
  watchedByMe: false,
  replayAllowed: false,
  allPlayersWatched: false,
  watchCount: 0,
  requiredWatchers: 2,
  ...overrides,
});

const competition = (overrides: Partial<CompetitionResponse> = {}): CompetitionResponse => ({
  competitionId: "c1",
  sessionId: "s1",
  type: "LEAGUE",
  status: "IN_PROGRESS",
  tournamentPhase: null,
  intermissionEndsAt: null,
  replaysUnlocked: false,
  championUserId: null,
  championNickname: null,
  matches: [match()],
  leaguePhase: "MATCHDAY_1",
  standings: [],
  ...overrides,
});

describe("leagueFlow", () => {
  it("routes to my matchday fixture", () => {
    const data = competition({
      leaguePhase: "MATCHDAY_1",
      matches: [match({ playable: true, matchId: "watch-me" })],
    });
    expect(resolveLeagueStep(data, "me")).toEqual({
      kind: "watch",
      matchId: "watch-me",
      replay: false,
    });
  });

  it("shows break during matchday intermission", () => {
    const data = competition({
      leaguePhase: "BREAK_AFTER_MD1",
      intermissionEndsAt: "2099-01-01T00:00:00Z",
    });
    expect(resolveLeagueStep(data, "me").kind).toBe("break");
  });

  it("formats playoff penalties", () => {
    expect(
      formatLeagueMatchScore(
        match({
          roundKey: "PLAYOFF_FINAL",
          homeScore: 1,
          awayScore: 1,
          homePenaltyScore: 4,
          awayPenaltyScore: 3,
        })
      )
    ).toBe("1 – 1 (4–3 pens)");
  });

  it("finds matchday from phase", () => {
    expect(matchdayFromPhase("MATCHDAY_2")).toBe(2);
    expect(matchdayFromPhase("MATCHDAY_3")).toBe(3);
    expect(matchdayFromPhase("COMPLETED")).toBe(0);
    expect(findMyMatchdayMatch([match({ roundKey: "MD2_B", awayUserId: "me" })], "MATCHDAY_2", "me")?.roundKey).toBe(
      "MD2_B"
    );
  });

  it("routesToPlayoffFinalWhenPlayable", () => {
    const data = competition({
      leaguePhase: "CHAMPIONSHIP_PLAYOFF",
      matches: [match({ roundKey: "PLAYOFF_FINAL", playable: true, matchId: "final-1" })],
    });
    expect(resolveLeagueStep(data, "me")).toEqual({
      kind: "watch",
      matchId: "final-1",
      replay: false,
    });
  });

  it("showsWaitingAfterPlayoffWatched", () => {
    const data = competition({
      leaguePhase: "CHAMPIONSHIP_PLAYOFF",
      matches: [match({ roundKey: "PLAYOFF_FINAL", playable: false, watchedByMe: true })],
    });
    expect(resolveLeagueStep(data, "me")).toEqual({ kind: "waiting", phase: "CHAMPIONSHIP_PLAYOFF" });
  });

  it("matchdayLabel_formatsRoundKey", () => {
    expect(matchdayLabel("PLAYOFF_FINAL")).toBe("Championship decider");
    expect(matchdayLabel("MD1_A")).toBe("MD1 A");
  });
});
