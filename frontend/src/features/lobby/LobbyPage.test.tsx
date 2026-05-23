import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { REACT_ROUTER_FUTURE_FLAGS } from "../../test/reactRouterFuture";
import { LobbyPage } from "./LobbyPage";

jest.mock("./hooks/useLobbyFlow", () => ({
  useLobbyFlow: () => ({
    session: { token: "t1", nickname: "Guest" },
    snapshot: null,
    rooms: [],
    loading: false,
    error: null,
    isConnected: false,
    showReconnecting: false,
    countdownSecondsLeft: 0,
    formations: ["FOUR_FOUR_TWO"],
    handleCreateRoom: jest.fn(),
    handleJoinRoom: jest.fn(),
    handleLeave: jest.fn(),
    handleStartAuction: jest.fn(),
    chooseFormation: jest.fn(),
    createGameMode: "DUEL",
    setCreateGameMode: jest.fn(),
    createMinPoolRating: 70,
    setCreateMinPoolRating: jest.fn(),
    createPoolLeague: "PREMIER_LEAGUE",
    setCreatePoolLeague: jest.fn(),
    poolMinRatingOptions: [{ minRating: 70, cardCount: 100, selectable: true }],
    poolMinRatingOptionsLoaded: true,
  }),
}));

describe("LobbyPage", () => {
  it("rendersCreateRoomControls", () => {
    render(
      <MemoryRouter future={REACT_ROUTER_FUTURE_FLAGS}>
        <LobbyPage />
      </MemoryRouter>
    );

    expect(screen.getByTestId("lobby-create-room")).toBeInTheDocument();
    expect(screen.getByText("Create Room")).toBeInTheDocument();
  });
});

export {};
