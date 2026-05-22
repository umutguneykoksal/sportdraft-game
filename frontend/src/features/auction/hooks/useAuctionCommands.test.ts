import { renderHook } from "@testing-library/react";
import { useAuctionCommands } from "./useAuctionCommands";

jest.mock("../../../shared/api", () => ({
  startRound: jest.fn(),
  placeBid: jest.fn(),
  passRound: jest.fn(),
  readyNextRound: jest.fn(),
  confirmMatchStart: jest.fn(),
}));

const api = jest.requireMock("../../../shared/api");

describe("useAuctionCommands", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("delegatesToSessionScopedApiCalls", async () => {
    const { result } = renderHook(() => useAuctionCommands("session-1"));

    await result.current.startRound();
    await result.current.placeBid("round-1", 5);
    await result.current.passRound("round-1");
    await result.current.readyNextRound("round-1");
    await result.current.confirmMatchStart();

    expect(api.startRound).toHaveBeenCalledWith("session-1");
    expect(api.placeBid).toHaveBeenCalledWith("session-1", "round-1", 5);
    expect(api.passRound).toHaveBeenCalledWith("session-1", "round-1");
    expect(api.readyNextRound).toHaveBeenCalledWith("session-1", "round-1");
    expect(api.confirmMatchStart).toHaveBeenCalledWith("session-1");
  });
});

export {};
