import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { REACT_ROUTER_FUTURE_FLAGS } from "../../test/reactRouterFuture";
import { LobbySessionPanel } from "./components/LobbySessionPanel";
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
  it("showsAuctionCountdownWithoutFormationGridWhenBothReady", () => {
    render(
      <LobbySessionPanel
        nickname="Guest"
        snapshot={baseSnapshot}
        maxPlayers={2}
        gameMode="DUEL"
        countdownSecondsLeft={5}
        formations={["FOUR_FOUR_TWO", "FOUR_THREE_THREE"]}
        loading={false}
        onChooseFormation={jest.fn()}
        onStartAuction={jest.fn()}
        onLeave={jest.fn()}
      />
    );

    expect(screen.getByTestId("lobby-auction-countdown")).toHaveTextContent("5");
    expect(screen.getByText("Auction starting...")).toBeInTheDocument();
    expect(screen.queryByTestId("lobby-formation-FOUR_FOUR_TWO")).not.toBeInTheDocument();
    expect(screen.queryByTestId("lobby-confirm-formation-ready")).not.toBeInTheDocument();
    expect(screen.getByTestId("lobby-leave")).toBeInTheDocument();
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
});

export {};
