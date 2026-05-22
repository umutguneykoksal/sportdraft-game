import { buildRoundOutcomeNotice } from "./buildRoundOutcomeNotice";
import type { AuctionRoundResponse } from "../../shared/api/contracts";

const baseRound: AuctionRoundResponse = {
  roundId: "r1",
  sessionId: "s1",
  cardId: "c1",
  cardName: "Haaland",
  position: "FWD",
  cardRating: 91,
  highestBidCents: 42,
  highestBidderId: "p1",
  highestBidderUserId: "user-a",
  currentTurnUserId: null,
  readyUserOneId: null,
  readyUserTwoId: null,
  endsAt: new Date().toISOString(),
  turnOpenAt: null,
  status: "SOLD",
  opponentCanBid: false,
  viewerCanBid: false,
  viewerCanTake: false,
  viewerCanPass: false,
  viewerCanFillCard: false,
  passedPlayerIds: [],
};

describe("buildRoundOutcomeNotice", () => {
  it("builds sold notice for the winning player", () => {
    const notice = buildRoundOutcomeNotice(
      { result: "SOLD", winnerUserId: "user-a", round: baseRound },
      "user-a",
      "Alice"
    );
    expect(notice?.kind).toBe("sold");
    expect(notice?.isMine).toBe(true);
    expect(notice?.winnerLabel).toBe("Alice");
    expect(notice?.amountCents).toBe(42);
  });

  it("uses opponent nickname in 4-player mode", () => {
    const notice = buildRoundOutcomeNotice(
      { result: "SOLD", winnerUserId: "user-b", round: baseRound },
      "user-a",
      "Alice",
      [
        { userId: "user-a", nickname: "Alice" },
        { userId: "user-b", nickname: "Bob" },
      ]
    );
    expect(notice?.winnerLabel).toBe("Bob");
    expect(notice?.isMine).toBe(false);
  });

  it("builds passed notice with friendly reason", () => {
    const notice = buildRoundOutcomeNotice(
      { result: "PASSED", reason: "both_passed", round: { ...baseRound, status: "PASSED" } },
      "user-a",
      "Alice"
    );
    expect(notice?.kind).toBe("passed");
    expect(notice?.detail).toBe("both players passed");
  });

  it("defaults passed detail when no reason", () => {
    const notice = buildRoundOutcomeNotice(
      { result: "PASSED", round: { ...baseRound, status: "PASSED", highestBidCents: 0 } },
      "user-a",
      "Alice"
    );
    expect(notice?.detail).toBe("no one bought this card");
  });
});
