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
  it("opensHowToPlayModal", async () => {
    const user = userEvent.setup();
    render(<LobbyEntryPanel {...baseProps} />);

    await user.click(screen.getByTestId("lobby-how-to-play"));

    expect(screen.getByTestId("how-to-play-modal")).toBeInTheDocument();
    expect(screen.getByText("Auction")).toBeInTheDocument();
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
