import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { LobbyEntryPanel, type LobbyEntryPanelProps } from "./LobbyEntryPanel";

const baseProps: LobbyEntryPanelProps = {
  nicknameInput: "Tester",
  onNicknameChange: jest.fn(),
  nicknameReady: true,
  createGameMode: "DUEL",
  onCreateGameModeChange: jest.fn(),
  createPoolLeague: "PREMIER_LEAGUE",
  onCreatePoolLeagueChange: jest.fn(),
  createMinPoolRating: 70,
  onCreateMinPoolRatingChange: jest.fn(),
  poolMinRatingOptions: [{ minRating: 70, cardCount: 100, selectable: true }],
  poolMinRatingOptionsLoaded: true,
  hasSelectablePoolRating: true,
  canCreateRoom: true,
  loading: false,
  rooms: [],
  onCreateRoom: jest.fn(),
  onJoinRoom: jest.fn(),
  onSpectateRoom: jest.fn(),
};

describe("LobbyEntryPanel", () => {
  it("allowsSpectateWithoutNickname", () => {
    render(
      <LobbyEntryPanel
        {...baseProps}
        nicknameInput=""
        nicknameReady={false}
        canCreateRoom={false}
        rooms={[
          {
            sessionId: "room-1",
            status: "AUCTIONING",
            playerCount: 2,
            maxPlayers: 2,
            playerNicknames: ["Alice", "Bob"],
            createdAt: "2026-01-01T00:00:00.000Z",
            spectatorCount: 0,
          },
        ]}
      />
    );

    expect(screen.getByTestId("lobby-spectate-room-1")).not.toBeDisabled();
    expect(screen.queryByTestId("lobby-join-room-1")).not.toBeInTheDocument();
  });

  it("showsNicknameRequiredHint", () => {
    render(<LobbyEntryPanel {...baseProps} nicknameInput="" nicknameReady={false} canCreateRoom={false} />);

    expect(screen.getByTestId("lobby-nickname-hint")).toHaveTextContent("Required to create or join a room");
    expect(screen.getByTestId("lobby-join-nickname-hint")).toHaveTextContent(/nickname above/i);
  });

  it("showsPoolLeagueSelector", () => {
    render(<LobbyEntryPanel {...baseProps} />);

    expect(screen.getByTestId("lobby-pool-league")).toBeInTheDocument();
    expect(screen.getByLabelText("League")).toBeInTheDocument();
  });

  it("opensHowToPlayModal", async () => {
    const user = userEvent.setup();
    render(<LobbyEntryPanel {...baseProps} />);

    await user.click(screen.getByTestId("lobby-how-to-play"));

    expect(screen.getByTestId("how-to-play-modal")).toBeInTheDocument();
    expect(screen.getByText("Game modes")).toBeInTheDocument();
    expect(screen.queryByText("Goal")).not.toBeInTheDocument();
  });

  it("opensFeedbackModal", async () => {
    const user = userEvent.setup();
    render(<LobbyEntryPanel {...baseProps} />);

    await user.click(screen.getByTestId("lobby-feedback"));

    expect(screen.getByTestId("feedback-modal")).toBeInTheDocument();
    expect(screen.getByTestId("feedback-message")).toBeInTheDocument();
  });
});

export {};
