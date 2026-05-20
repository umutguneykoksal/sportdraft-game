import { deriveViewerFlags } from "./deriveViewerFlags";
import type { AuctionRoundResponse } from "../../shared/api/contracts";

const baseRound = (overrides: Partial<AuctionRoundResponse> = {}): AuctionRoundResponse => ({
  roundId: "r1",
  sessionId: "s1",
  cardId: "c1",
  cardName: "Test",
  position: "FWD",
  highestBidCents: 5,
  highestBidderId: null,
  highestBidderUserId: "other",
  currentTurnUserId: "me",
  readyUserOneId: null,
  readyUserTwoId: null,
  endsAt: new Date(Date.now() + 60_000).toISOString(),
  turnOpenAt: new Date(Date.now() + 60_000).toISOString(),
  status: "OPEN",
  opponentCanBid: true,
  viewerCanBid: false,
  viewerCanTake: false,
  viewerCanPass: false,
  viewerCanFillCard: false,
  passedPlayerIds: [],
  cardRating: 80,
  ...overrides,
});

describe("deriveViewerFlags", () => {
  it("allows bid when budget matches minor units (4-player scale)", () => {
    const flags = deriveViewerFlags(
      baseRound({ highestBidCents: 5, currentTurnUserId: "me" }),
      "me",
      [],
      "FOUR_FOUR_TWO",
      95,
      0,
      true
    );
    expect(flags.viewerCanBid).toBe(true);
  });

  it("blocks bid for the current highest bidder during open bidding", () => {
    const flags = deriveViewerFlags(
      baseRound({
        highestBidCents: 12,
        highestBidderUserId: "me",
        currentTurnUserId: "other",
      }),
      "me",
      [],
      "FOUR_FOUR_TWO",
      95,
      0,
      true
    );
    expect(flags.viewerCanBid).toBe(false);
    expect(flags.viewerCanPass).toBe(false);
  });

  it("blocks bid when budget is wrongly scaled down (old /100 bug)", () => {
    const flags = deriveViewerFlags(
      baseRound({ highestBidCents: 5, currentTurnUserId: "me" }),
      "me",
      [],
      "FOUR_FOUR_TWO",
      0,
      0
    );
    expect(flags.viewerCanBid).toBe(false);
  });
});
