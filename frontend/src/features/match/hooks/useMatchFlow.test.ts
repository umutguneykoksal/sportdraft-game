import { renderHook, waitFor, act } from "@testing-library/react";
import { ApiError } from "../../../shared/api/errors";
import { useMatchFlow } from "./useMatchFlow";

jest.mock("../../../shared/api", () => ({
  getMatchResult: jest.fn(),
}));

jest.mock("../../../shared/sse", () => ({
  useSessionStream: jest.fn(() => ({ events: [], isConnected: false })),
}));

const { getMatchResult } = jest.requireMock("../../../shared/api");
const { useSessionStream } = jest.requireMock("../../../shared/sse");

describe("useMatchFlow", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    useSessionStream.mockReturnValue({ events: [], isConnected: false });
  });

  it("shares match result poll interval with spectate replay cadence", async () => {
    const { MATCH_RESULT_POLL_MS, MATCH_REPLAY_POLL_MS } = await import("../../../shared/realtime/syncConstants");
    expect(MATCH_RESULT_POLL_MS).toBe(MATCH_REPLAY_POLL_MS);
    expect(MATCH_RESULT_POLL_MS).toBe(2_000);
  });

  it("keeps polling on transient fetch errors until consecutive limit", async () => {
    jest.useFakeTimers();
    try {
      getMatchResult.mockRejectedValue(new Error("Match result not ready"));

      const { result } = renderHook(() => useMatchFlow("session-1"));

      await waitFor(() => {
        expect(getMatchResult).toHaveBeenCalledWith("session-1");
      });

      expect(result.current.loadError).toBeNull();

      for (let i = 0; i < 4; i++) {
        await act(async () => {
          jest.advanceTimersByTime(2_000);
          await Promise.resolve();
        });
      }

      await waitFor(() => {
        expect(result.current.loadError).toBe("Match result not ready");
      });
    } finally {
      jest.useRealTimers();
    }
  });

  it("stopsImmediatelyOnForbidden", async () => {
    getMatchResult.mockRejectedValue(new ApiError("Forbidden", 403));

    const { result } = renderHook(() => useMatchFlow("session-1"));

    await waitFor(() => {
      expect(result.current.loadError).toBe("Forbidden");
    });

    expect(getMatchResult).toHaveBeenCalledTimes(1);
  });

  it("stopsImmediatelyOnUnauthorized", async () => {
    getMatchResult.mockRejectedValue(new ApiError("Unauthorized", 401));

    const { result } = renderHook(() => useMatchFlow("session-1"));

    await waitFor(() => {
      expect(result.current.loadError).toBe("Unauthorized");
    });

    expect(getMatchResult).toHaveBeenCalledTimes(1);
  });

  it("timesOutAfterMatchCompletedWhenResultNeverArrives", async () => {
    jest.useFakeTimers();
    try {
      const { SIMULATION_STALL_WARNING_MS } = await import("../../../shared/realtime/syncConstants");
      useSessionStream.mockReturnValue({
        events: [{ eventType: "match_completed", seq: 10 }],
        isConnected: true,
      });
      getMatchResult.mockResolvedValue(null);

      const { result } = renderHook(() => useMatchFlow("session-1"));

      await waitFor(() => {
        expect(getMatchResult).toHaveBeenCalled();
      });

      act(() => {
        jest.advanceTimersByTime(SIMULATION_STALL_WARNING_MS + 5_000);
      });

      await waitFor(() => {
        expect(result.current.loadError).toMatch(/longer than expected/i);
      });
    } finally {
      jest.useRealTimers();
    }
  });

  it("loadsResultOnMatchCompletedEvent", async () => {
    useSessionStream.mockReturnValue({
      events: [{ eventType: "match_completed", seq: 10 }],
      isConnected: true,
    });
    getMatchResult.mockResolvedValue({
      homeScore: 2,
      awayScore: 1,
      myTeam: [],
      opponentTeam: [],
      myFormation: "FOUR_FOUR_TWO",
      opponentFormation: "FOUR_THREE_THREE",
      myNickname: "You",
      opponentNickname: "Opponent",
    });

    const { result } = renderHook(() => useMatchFlow("session-1"));

    await waitFor(() => {
      expect(result.current.ready).toBe(true);
    });

    expect(result.current.homeScore).toBe(2);
    expect(result.current.awayScore).toBe(1);
  });
});

export {};
