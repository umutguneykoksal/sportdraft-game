import { renderHook, waitFor } from "@testing-library/react";
import { useAuctionOrchestrator } from "./useAuctionOrchestrator";
import { ApiError } from "../../../shared/api/errors";

jest.mock("../../../shared/api", () => ({
  getSessionSnapshot: jest.fn(),
  getAuctionState: jest.fn(),
}));

jest.mock("../../../shared/sse", () => ({
  useSessionStream: () => ({ events: [], isConnected: false }),
}));

const { getSessionSnapshot, getAuctionState } = jest.requireMock("../../../shared/api");

describe("useAuctionOrchestrator", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("surfaces auction sync errors from the API", async () => {
    getSessionSnapshot.mockRejectedValue(new ApiError("Not your turn to bid", 409));
    getAuctionState.mockResolvedValue({ currentRound: null });

    const { result } = renderHook(() => useAuctionOrchestrator("session-1"));

    await waitFor(() => {
      expect(result.current.error).toBe("Not your turn to bid");
    });
  });

  it("surfaces bid validation errors as auction sync failures", async () => {
    getSessionSnapshot.mockResolvedValue({
      sessionId: "session-1",
      status: "AUCTIONING",
    });
    getAuctionState.mockRejectedValue(new ApiError("Bid is below minimum increment", 422));

    const { result } = renderHook(() => useAuctionOrchestrator("session-1"));

    await waitFor(() => {
      expect(result.current.error).toBe("Bid is below minimum increment");
    });
  });
});

export {};
