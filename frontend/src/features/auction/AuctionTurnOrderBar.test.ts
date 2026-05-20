import { buildDuelTurnOrderEntries, buildTurnOrderEntries } from "./AuctionTurnOrderBar";

describe("buildTurnOrderEntries", () => {
  it("uses auction playerViews order when present", () => {
    const entries = buildTurnOrderEntries(
      [
        { userId: "a", nickname: "A", budgetRemaining: 90, rosterSize: 1, currentTurn: true, formation: "FOUR_FOUR_TWO", team: [] },
        { userId: "b", nickname: "B", budgetRemaining: 100, rosterSize: 0, currentTurn: false, formation: "FOUR_FOUR_TWO", team: [] },
      ],
      [{ userId: "b", nickname: "B", seat: 0 }, { userId: "a", nickname: "A", seat: 1 }],
      "a",
      "a"
    );
    expect(entries.map((e) => e.nickname)).toEqual(["A", "B"]);
    expect(entries[0].isCurrentTurn).toBe(true);
    expect(entries[0].isMe).toBe(true);
  });

  it("falls back to lobby seat order before auction state loads", () => {
    const entries = buildTurnOrderEntries(
      [],
      [
        { userId: "h", nickname: "Host", seat: 0 },
        { userId: "p2", nickname: "P2", seat: 1 },
        { userId: "p3", nickname: "P3", seat: 2 },
      ],
      "p2",
      "h"
    );
    expect(entries.map((e) => e.nickname)).toEqual(["Host", "P2", "P3"]);
    expect(entries[1].isCurrentTurn).toBe(true);
  });

  it("builds duel order with budgets for two players", () => {
    const entries = buildDuelTurnOrderEntries(
      [
        { userId: "me", nickname: "Alice", seat: 0 },
        { userId: "opp", nickname: "Bob", seat: 1 },
      ],
      "opp",
      "me",
      80,
      2,
      95,
      1
    );
    expect(entries).toHaveLength(2);
    expect(entries[0].nickname).toBe("Alice");
    expect(entries[0].budgetRemaining).toBe(80);
    expect(entries[1].isCurrentTurn).toBe(true);
    expect(entries[1].budgetRemaining).toBe(95);
  });
});
