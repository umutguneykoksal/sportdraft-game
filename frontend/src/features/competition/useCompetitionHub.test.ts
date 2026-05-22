import { act, renderHook, waitFor } from "@testing-library/react";
import { useCompetitionHub } from "./useCompetitionHub";
import type { CompetitionResponse } from "../../shared/api/contracts";

const mockNavigate = jest.fn();

jest.mock("react-router-dom", () => ({
  useNavigate: () => mockNavigate,
}));

jest.mock("../../shared/api", () => ({
  getClientUserId: () => "user-1",
  getCompetitionBySession: jest.fn(),
}));

jest.mock("../../shared/sse", () => ({
  useSessionStream: () => ({ events: [], isConnected: false }),
}));

const { getCompetitionBySession } = jest.requireMock("../../shared/api");

const competition: CompetitionResponse = {
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
};

describe("useCompetitionHub", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    getCompetitionBySession.mockResolvedValue(competition);
  });

  it("loadsCompetitionOnMount", async () => {
    const { result } = renderHook(() =>
      useCompetitionHub("s1", {
        sseEventTypes: ["tournament_started"],
        resolveStep: () => ({ kind: "hub" }),
        matchPath: (sessionId, matchId) => `/t/${sessionId}/m/${matchId}`,
      })
    );

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.competition).toEqual(competition);
    expect(result.current.error).toBeNull();
  });

  it("surfacesLoadErrors", async () => {
    getCompetitionBySession.mockRejectedValue(new Error("Network down"));

    const { result } = renderHook(() =>
      useCompetitionHub("s1", {
        sseEventTypes: [],
        resolveStep: () => ({ kind: "hub" }),
        matchPath: () => "/",
      })
    );

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBe("Network down");
  });

  it("autoNavigatesToWatchStep", async () => {
    const { result } = renderHook(() =>
      useCompetitionHub("s1", {
        sseEventTypes: [],
        resolveStep: () => ({ kind: "watch", matchId: "m1", replay: false }),
        matchPath: (sessionId, matchId) => `/t/${sessionId}/m/${matchId}`,
      })
    );

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(mockNavigate).toHaveBeenCalledWith("/t/s1/m/m1", { replace: true });
  });

  it("refreshReloadsCompetition", async () => {
    const { result } = renderHook(() =>
      useCompetitionHub("s1", {
        sseEventTypes: [],
        resolveStep: () => ({ kind: "hub" }),
        matchPath: () => "/",
      })
    );

    await waitFor(() => expect(result.current.loading).toBe(false));
    await act(async () => {
      await result.current.refresh();
    });
    expect(getCompetitionBySession).toHaveBeenCalledTimes(2);
  });

  it("tracksIntermissionCountdown", async () => {
    jest.useFakeTimers();
    getCompetitionBySession.mockResolvedValue({
      ...competition,
      intermissionEndsAt: new Date(Date.now() + 5000).toISOString(),
    });

    const { result } = renderHook(() =>
      useCompetitionHub("s1", {
        sseEventTypes: [],
        resolveStep: () => ({ kind: "hub" }),
        matchPath: () => "/",
      })
    );

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.secondsLeft).toBeGreaterThan(0);
    jest.useRealTimers();
  });
});

export {};
