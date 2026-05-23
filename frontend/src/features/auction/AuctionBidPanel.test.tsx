import { render, screen } from "@testing-library/react";
import { AuctionBidPanel, type AuctionBidPanelProps } from "./AuctionBidPanel";
import type { AuctionRoundResponse } from "../../shared/api";

const baseRound: AuctionRoundResponse = {
  roundId: "r1",
  sessionId: "s1",
  cardId: "c1",
  position: "FWD",
  eligiblePositions: ["FWD"],
  cardName: "Test Player",
  cardRating: 85,
  status: "OPEN",
  highestBidCents: 0,
  highestBidderId: null,
  highestBidderUserId: null,
  currentTurnUserId: "u1",
  readyUserOneId: null,
  readyUserTwoId: null,
  opponentCanBid: true,
  viewerCanBid: false,
  viewerCanTake: false,
  viewerCanPass: true,
  viewerCanFillCard: false,
  passedPlayerIds: [],
  startsAt: new Date().toISOString(),
  endsAt: new Date(Date.now() + 30_000).toISOString(),
  turnOpenAt: null,
};

function renderPanel(overrides: Partial<AuctionBidPanelProps> = {}) {
  const props: AuctionBidPanelProps = {
    round: baseRound,
    auctionPhaseActive: true,
    error: null,
    secondsLeft: 30,
    timeExtended: false,
    timerPercent: 100,
    timerColor: "bg-accent-green",
    minimumBid: 1,
    budget: 50,
    roundIsOpen: true,
    controlsLocked: false,
    interactionLocked: false,
    inCardReveal: false,
    loading: false,
    canBid: false,
    couldBid: false,
    canPass: true,
    canFillCard: false,
    showTakePanel: false,
    showNoSlotBanner: true,
    mySquadFull: false,
    turnIsOpen: false,
    isMyTurn: true,
    inTimerGrace: false,
    amount: "1",
    setAmount: jest.fn(),
    turnStatusMessage: () => "No slot for this card — pass to skip",
    onTake: jest.fn(),
    onBid: jest.fn(),
    onPass: jest.fn(),
    ...overrides,
  };

  return render(<AuctionBidPanel {...props} />);
}

describe("AuctionBidPanel", () => {
  it("showsSingleStatusDuringCardRevealWhenNoSlot", () => {
    renderPanel({
      interactionLocked: true,
      inCardReveal: true,
      showNoSlotBanner: true,
    });

    expect(screen.getByTestId("auction-status-strip")).toHaveTextContent(
      "New card — bidding opens soon"
    );
    expect(screen.queryByText("No open slot for this card in your formation")).not.toBeInTheDocument();
  });

  it("showsNoSlotBannerWhenNotLocked", () => {
    renderPanel({
      interactionLocked: false,
      inCardReveal: false,
      showNoSlotBanner: true,
    });

    expect(screen.getByText("No open slot for this card in your formation")).toBeInTheDocument();
    expect(screen.queryByText("New card — bidding opens soon")).not.toBeInTheDocument();
  });
});

export {};
