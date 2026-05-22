import { renderHook, waitFor } from "@testing-library/react";
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

  it("keeps result unset when match result fetch fails", async () => {
    getMatchResult.mockRejectedValue(new Error("Match result not ready"));

    const { result } = renderHook(() => useMatchFlow("session-1"));

    await waitFor(() => {
      expect(getMatchResult).toHaveBeenCalledWith("session-1");
    });

    expect(result.current.ready).toBe(false);
    expect(result.current.homeScore).toBeNull();
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
