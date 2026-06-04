import { render, screen } from "@testing-library/react";
import { LobbySessionPanel } from "./components/LobbySessionPanel";
import { ALL_FORMATIONS } from "../../shared/formations";
import type { SessionSnapshotResponse } from "../../shared/api/contracts";

const baseSnapshot: SessionSnapshotResponse = {
  sessionId: "s1",
  status: "FORMATION_SELECT",
  joined: true,
  playerCount: 2,
  maxPlayers: 2,
  gameMode: "DUEL",
  myFormation: "FOUR_FOUR_TWO",
  matchStartConfirmedByMe: true,
  matchStartReadyCount: 2,
  poolLeague: "PREMIER_LEAGUE",
  minPoolRating: 70,
  countdownEndsAt: null,
  matchStartDeadlineAt: null,
};

describe("LobbySessionPanel", () => {
  it("showsTenSecondAuctionCountdownForConfirmedPlayerWhenOpponentNotReady", () => {
    render(
      <LobbySessionPanel
        nickname="Guest"
        snapshot={{
          ...baseSnapshot,
          matchStartReadyCount: 1,
          matchStartConfirmedByMe: true,
          countdownEndsAt: "2026-01-01T00:00:10.000Z",
        }}
        maxPlayers={2}
        gameMode="DUEL"
        countdownSecondsLeft={10}
        formations={["FOUR_FOUR_TWO", "FOUR_THREE_THREE"]}
        loading={false}
        onChooseFormation={jest.fn()}
        onStartAuction={jest.fn()}
        onLeave={jest.fn()}
      />
    );

    expect(screen.getByTestId("lobby-auction-countdown")).toHaveTextContent("10");
    expect(screen.queryByTestId("lobby-formation-FOUR_FOUR_TWO")).not.toBeInTheDocument();
    expect(screen.getByTestId("lobby-leave")).toBeInTheDocument();
  });

  it("keepsFormationGridForOpponentDuringFirstReadyCountdown", () => {
    render(
      <LobbySessionPanel
        nickname="Guest"
        snapshot={{
          ...baseSnapshot,
          matchStartReadyCount: 1,
          matchStartConfirmedByMe: false,
          countdownEndsAt: "2026-01-01T00:00:10.000Z",
        }}
        maxPlayers={2}
        gameMode="DUEL"
        countdownSecondsLeft={8}
        formations={["FOUR_FOUR_TWO"]}
        loading={false}
        onChooseFormation={jest.fn()}
        onStartAuction={jest.fn()}
        onLeave={jest.fn()}
      />
    );

    expect(screen.queryByTestId("lobby-auction-countdown")).not.toBeInTheDocument();
    expect(screen.getByTestId("lobby-formation-FOUR_FOUR_TWO")).toBeInTheDocument();
    expect(screen.getByTestId("lobby-confirm-formation-ready")).toBeInTheDocument();
  });

  it("showsLeaveDuringAuctionCountdown", () => {
    render(
      <LobbySessionPanel
        nickname="Guest"
        snapshot={baseSnapshot}
        maxPlayers={2}
        gameMode="DUEL"
        countdownSecondsLeft={5}
        formations={["FOUR_FOUR_TWO"]}
        loading={false}
        onChooseFormation={jest.fn()}
        onStartAuction={jest.fn()}
        onLeave={jest.fn()}
      />
    );

    expect(screen.getByTestId("lobby-leave")).toBeInTheDocument();
  });

  it("placesWaitingHintInFooterWithoutOverlappingGrid", () => {
    render(
      <LobbySessionPanel
        nickname="Guest"
        snapshot={{
          ...baseSnapshot,
          matchStartReadyCount: 1,
          matchStartConfirmedByMe: true,
        }}
        maxPlayers={2}
        gameMode="DUEL"
        countdownSecondsLeft={null}
        formations={["FOUR_FOUR_TWO"]}
        loading={false}
        onChooseFormation={jest.fn()}
        onStartAuction={jest.fn()}
        onLeave={jest.fn()}
      />
    );

    expect(screen.getByText("Waiting for opponent to confirm...")).toBeInTheDocument();
    expect(screen.getByTestId("lobby-formation-FOUR_FOUR_TWO")).toBeInTheDocument();
  });

  it("rendersAllFormationCardsDuringPick", () => {
    render(
      <LobbySessionPanel
        nickname="Guest"
        snapshot={{
          ...baseSnapshot,
          matchStartReadyCount: 0,
          matchStartConfirmedByMe: false,
        }}
        maxPlayers={2}
        gameMode="DUEL"
        countdownSecondsLeft={null}
        formations={ALL_FORMATIONS}
        loading={false}
        onChooseFormation={jest.fn()}
        onStartAuction={jest.fn()}
        onLeave={jest.fn()}
      />
    );

    for (const formation of ALL_FORMATIONS) {
      expect(screen.getByTestId(`lobby-formation-${formation}`)).toBeInTheDocument();
    }
    expect(screen.getByTestId("lobby-confirm-formation-ready")).toBeInTheDocument();
  });

  it("showsPoolLabelOnSeparateLine", () => {
    render(
      <LobbySessionPanel
        nickname="Guest"
        snapshot={baseSnapshot}
        maxPlayers={2}
        gameMode="DUEL"
        countdownSecondsLeft={null}
        formations={ALL_FORMATIONS}
        loading={false}
        onChooseFormation={jest.fn()}
        onStartAuction={jest.fn()}
        onLeave={jest.fn()}
      />
    );

    expect(screen.getByText(/Premier League · 70\+ OVR/i)).toBeInTheDocument();
  });
});

export {};
