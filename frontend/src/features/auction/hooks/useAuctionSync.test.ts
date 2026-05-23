import { renderHook, waitFor } from "@testing-library/react";
import { useAuctionSync } from "./useAuctionSync";
import * as api from "../../../shared/api";
import * as sse from "../../../shared/sse";

jest.mock("../../../shared/api");
jest.mock("../../../shared/sse");

describe("useAuctionSync", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    (sse.useSessionStream as jest.Mock).mockReturnValue({ events: [], isConnected: true });
    (api.getSessionSnapshot as jest.Mock).mockResolvedValue({ sessionId: "session-1", status: "AUCTIONING" });
    (api.getAuctionState as jest.Mock).mockResolvedValue({ currentRound: { id: "round-1" } });
  });

  it("loads snapshot and auction state on mount", async () => {
    const { result } = renderHook(() => useAuctionSync("session-1"));

    await waitFor(() => expect(result.current.snapshot).not.toBeNull());
    expect(result.current.round).toEqual({ id: "round-1" });
    expect(api.getSessionSnapshot).toHaveBeenCalledWith("session-1");
    expect(api.getAuctionState).toHaveBeenCalledWith("session-1");
  });

  it("returns null state when sessionId is missing", () => {
    const { result } = renderHook(() => useAuctionSync(null));
    expect(result.current.snapshot).toBeNull();
    expect(result.current.round).toBeNull();
  });
});
