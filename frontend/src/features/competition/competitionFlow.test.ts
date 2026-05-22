import { formatMatchScore, resolveCompetitionStep } from "./competitionFlow";
import type { CompetitionMatchResponse } from "../../shared/api/contracts";

const match = (overrides: Partial<CompetitionMatchResponse> = {}): CompetitionMatchResponse => ({
  matchId: "m1",
  roundKey: "FINAL",
  homeUserId: "h1",
  homeNickname: "Home",
  awayUserId: "a1",
  awayNickname: "Away",
  homeScore: 2,
  awayScore: 1,
  homePenaltyScore: null,
  awayPenaltyScore: null,
  status: "COMPLETED",
  playable: false,
  watchedByMe: true,
  replayAllowed: false,
  allPlayersWatched: true,
  watchCount: 2,
  requiredWatchers: 2,
  ...overrides,
});

describe("competitionFlow", () => {
  it("formatMatchScore_returnsDashWhenScoresMissing", () => {
    expect(formatMatchScore(match({ homeScore: null, awayScore: null }))).toBe("—");
  });

  it("formatMatchScore_showsRegulationScore", () => {
    expect(formatMatchScore(match())).toBe("2 – 1");
  });

  it("formatMatchScore_showsPenaltiesOnDraw", () => {
    expect(
      formatMatchScore(
        match({
          homeScore: 1,
          awayScore: 1,
          homePenaltyScore: 4,
          awayPenaltyScore: 3,
          roundKey: "FINAL",
        }),
        { penaltyRoundKey: "FINAL" }
      )
    ).toBe("1 – 1 (4–3 pens)");
  });

  it("resolveCompetitionStep_returnsPauseDuringIntermission", () => {
    const step = resolveCompetitionStep(
      { intermissionEndsAt: "2099-01-01T00:00:00.000Z", matches: [] },
      {
        pausePhases: ["INTERMISSION"],
        replayPhase: "REPLAYS",
        phase: "INTERMISSION",
        myUserId: "u1",
        resolveActiveMatch: () => undefined,
      }
    );
    expect(step).toEqual({ kind: "pause", endsAt: "2099-01-01T00:00:00.000Z" });
  });

  it("resolveCompetitionStep_routesToWatchWhenPlayable", () => {
    const step = resolveCompetitionStep(
      { intermissionEndsAt: null, matches: [match({ playable: true, matchId: "semi-1" })] },
      {
        pausePhases: ["INTERMISSION"],
        replayPhase: "REPLAYS",
        phase: "SEMIS",
        myUserId: "h1",
        resolveActiveMatch: (matches) => matches[0],
      }
    );
    expect(step).toEqual({ kind: "watch", matchId: "semi-1", replay: false });
  });

  it("resolveCompetitionStep_returnsHubOnReplayPhase", () => {
    const step = resolveCompetitionStep(
      { intermissionEndsAt: null, matches: [] },
      {
        pausePhases: ["INTERMISSION"],
        replayPhase: "REPLAYS",
        phase: "REPLAYS",
        myUserId: "u1",
        resolveActiveMatch: () => undefined,
      }
    );
    expect(step).toEqual({ kind: "hub" });
  });
});

export {};
