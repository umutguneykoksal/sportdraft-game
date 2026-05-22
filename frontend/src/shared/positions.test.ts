import type { AuctionTeamCardResponse } from "../api/contracts";
import { orderedEligiblePositions, openEligibleHighlights } from "./positions";

describe("positions helpers", () => {
  it("orderedEligiblePositions_putsPrimaryFirst", () => {
    expect(orderedEligiblePositions("FWD", ["CM", "FWD", "CAM"])).toEqual(["FWD", "CAM", "CM"]);
  });

  it("orderedEligiblePositions_fallsBackToPrimaryOnly", () => {
    expect(orderedEligiblePositions("FWD")).toEqual(["FWD"]);
    expect(orderedEligiblePositions("FWD", [])).toEqual(["FWD"]);
  });

  it("openEligibleHighlights_returnsOpenSlots", () => {
    const open = openEligibleHighlights("FOUR_FOUR_TWO", [], ["FWD", "CM"]);
    expect(open).toContain("FWD");
    expect(open).toContain("CM");
  });

  it("openEligibleHighlights_returnsUndefinedWhenNoEligible", () => {
    expect(openEligibleHighlights("FOUR_FOUR_TWO", [], undefined)).toBeUndefined();
    expect(openEligibleHighlights("FOUR_FOUR_TWO", [], [])).toBeUndefined();
  });

  it("openEligibleHighlights_returnsUndefinedWhenSlotFull", () => {
    const cards: AuctionTeamCardResponse[] = [
      {
        cardId: "gk-1",
        cardName: "Keeper",
        position: "GK",
        rating: 80,
        amountCents: 10,
      },
    ];
    expect(openEligibleHighlights("FOUR_FOUR_TWO", cards, ["GK"])).toBeUndefined();
  });
});

export {};
