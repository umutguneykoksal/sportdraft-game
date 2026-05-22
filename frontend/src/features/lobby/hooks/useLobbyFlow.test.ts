import { act, renderHook, waitFor } from "@testing-library/react";
import { useLobbyFlow } from "./useLobbyFlow";
import { ApiError } from "../../../shared/api/errors";

jest.mock("react-router-dom", () => ({
  useNavigate: () => jest.fn(),
}));

jest.mock("../../../shared/api", () => ({
  createRoom: jest.fn(),
  joinRoom: jest.fn(),
  leaveLobby: jest.fn(),
  listLobbies: jest.fn().mockResolvedValue([]),
  getPoolMinRatingOptions: jest.fn().mockResolvedValue({ options: [] }),
  getSessionSnapshot: jest.fn(),
  selectFormation: jest.fn(),
  confirmFormationReady: jest.fn(),
}));

jest.mock("../../../shared/sse", () => ({
  useSessionStream: () => ({ events: [], isConnected: false }),
}));

jest.mock("../../../shared/session", () => ({
  getOrCreateClientSession: () => ({ nickname: "Guest", userId: "user-1" }),
  getActiveSessionId: () => null,
  setActiveSessionId: jest.fn(),
  clearActiveSessionId: jest.fn(),
}));

const { joinRoom } = jest.requireMock("../../../shared/api");

describe("useLobbyFlow", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("surfaces room full join errors", async () => {
    joinRoom.mockRejectedValue(new ApiError("Room is full", 409));

    const { result } = renderHook(() => useLobbyFlow());

    await act(async () => {
      await result.current.handleJoinRoom("room-1");
    });

    await waitFor(() => {
      expect(result.current.error).toBe("Room is full");
      expect(result.current.loading).toBe(false);
    });
  });
});

export {};
