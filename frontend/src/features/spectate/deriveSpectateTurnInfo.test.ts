import { deriveSpectateTurnMessage, nicknameForUserId } from "./deriveSpectateTurnInfo";
import type { AuctionRoundResponse, SpectateStateResponse } from "../../shared/api/contracts";

const state: SpectateStateResponse = {
  status: "AUCTIONING",
  currentRound: null,
  homeTeam: [],
  awayTeam: [],
  homeFormation: "FOUR_FOUR_TWO",
  awayFormation: "FOUR_FOUR_TWO",
  homeUserId: "home-id",
  awayUserId: "away-id",
  homeNickname: "Alice",
  awayNickname: "Bob",
  homeBudget: 50,
  awayBudget: 60,
  homeScore: 0,
  awayScore: 0,
};

const baseRound: AuctionRoundResponse = {
  roundId: "r1",
  sessionId: "s1",
  cardId: "c1",
  cardName: "Player",
  position: "FWD",
  cardRating: 80,
  highestBidCents: 0,
  highestBidderId: null,
  highestBidderUserId: null,
  currentTurnUserId: "home-id",
  readyUserOneId: null,
  readyUserTwoId: null,
  endsAt: new Date().toISOString(),
  startsAt: null,
  turnOpenAt: null,
  status: "OPEN",
  opponentCanBid: true,
  viewerCanBid: false,
  viewerCanTake: false,
  viewerCanPass: false,
  viewerCanFillCard: true,
  passedPlayerIds: [],
};

describe("nicknameForUserId", () => {
  it("maps home and away user ids to nicknames", () => {
    expect(nicknameForUserId("home-id", state)).toBe("Alice");
    expect(nicknameForUserId("away-id", state)).toBe("Bob");
    expect(nicknameForUserId("unknown", state)).toBeNull();
  });
});

describe("deriveSpectateTurnMessage", () => {
  it("shows opening turn holder", () => {
    const info = deriveSpectateTurnMessage(baseRound, state, false);
    expect(info.message).toBe("Alice's turn to bid");
    expect(info.tone).toBe("active");
  });

  it("shows leader and current turn after a bid", () => {
    const round = {
      ...baseRound,
      highestBidCents: 5,
      highestBidderUserId: "home-id",
      currentTurnUserId: "away-id",
    };
    const info = deriveSpectateTurnMessage(round, state, false);
    expect(info.message).toBe("Alice leads at 5 cr — Bob's turn");
  });

  it("shows open bidding when turn window expired", () => {
    const info = deriveSpectateTurnMessage(baseRound, state, true);
    expect(info.message).toBe("Bidding open to both players!");
    expect(info.tone).toBe("open");
  });
});
