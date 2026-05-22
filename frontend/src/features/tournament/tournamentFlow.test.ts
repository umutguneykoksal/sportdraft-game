import {
  findFinal,
  findMySemi,
  formatMatchScore,
  resolveTournamentStep,
} from "./tournamentFlow";
import type { CompetitionMatchResponse, CompetitionResponse } from "../../shared/api/contracts";

const semi = (overrides: Partial<CompetitionMatchResponse> = {}): CompetitionMatchResponse => ({
  matchId: "semi-1",
  roundKey: "SEMI_1",
  homeUserId: "me",
  homeNickname: "Me",
  awayUserId: "p2",
  awayNickname: "P2",
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
  type: "TOURNAMENT",
  status: "IN_PROGRESS",
  tournamentPhase: "SEMIS",
  intermissionEndsAt: null,
  replaysUnlocked: false,
  championUserId: null,
  championNickname: null,
  matches: [],
  ...overrides,
});

describe("tournamentFlow", () => {
  it("routes viewer to their own semi-final first", () => {
    const data = competition({
      matches: [
        semi({ matchId: "semi-1", homeUserId: "me" }),
        semi({ matchId: "semi-2", roundKey: "SEMI_2", homeUserId: "p3", awayUserId: "p4" }),
      ],
    });
    expect(resolveTournamentStep(data, "me")).toEqual({
      kind: "watch",
      matchId: "semi-1",
      replay: false,
    });
  });

  it("routes viewer to mandatory semi watch before intermission", () => {
    const data = competition({
      matches: [
        semi({ matchId: "semi-1", homeUserId: "me", watchedByMe: false, playable: true }),
        semi({ matchId: "semi-2", roundKey: "SEMI_2", homeUserId: "p3", awayUserId: "p4", watchedByMe: true }),
      ],
    });
    expect(resolveTournamentStep(data, "me")).toEqual({
      kind: "watch",
      matchId: "semi-1",
      replay: false,
    });
  });

  it("shows intermission when phase is INTERMISSION", () => {
    const data = competition({
      tournamentPhase: "INTERMISSION",
      intermissionEndsAt: "2099-01-01T00:00:15Z",
      matches: [semi({ homeScore: 2, awayScore: 1 })],
    });
    expect(resolveTournamentStep(data, "me")).toEqual({
      kind: "intermission",
      endsAt: "2099-01-01T00:00:15Z",
    });
  });

  it("routes to final during FINAL phase", () => {
    const data = competition({
      tournamentPhase: "FINAL",
      matches: [
        semi({ matchId: "semi-1", homeUserId: "me", watchedByMe: true, playable: false }),
        semi({ matchId: "final", roundKey: "FINAL", homeUserId: "me", awayUserId: "p4", playable: true }),
      ],
    });
    expect(resolveTournamentStep(data, "me")).toEqual({
      kind: "watch",
      matchId: "final",
      replay: false,
    });
  });

  it("returns hub when replays unlocked", () => {
    const data = competition({ tournamentPhase: "REPLAYS", replaysUnlocked: true, matches: [semi()] });
    expect(resolveTournamentStep(data, "me")).toEqual({ kind: "hub" });
  });

  it("formats penalty scores", () => {
    expect(
      formatMatchScore({
        ...semi(),
        homeScore: 1,
        awayScore: 1,
        homePenaltyScore: 4,
        awayPenaltyScore: 3,
      })
    ).toBe("1 – 1 (4–3 pens)");
  });

  it("finds viewer semi and final helpers", () => {
    const matches = [
      semi({ matchId: "semi-1", homeUserId: "me" }),
      semi({ matchId: "final", roundKey: "FINAL", homeUserId: "me", awayUserId: "p4" }),
    ];
    expect(findMySemi(matches, "me")?.matchId).toBe("semi-1");
    expect(findFinal(matches)?.matchId).toBe("final");
  });
});
