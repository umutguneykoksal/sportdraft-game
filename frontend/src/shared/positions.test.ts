import { orderedEligiblePositions, openEligibleHighlights } from "./positions";

describe("positions helpers", () => {
  it("orderedEligiblePositions_putsPrimaryFirst", () => {
    expect(orderedEligiblePositions("FWD", ["CM", "FWD", "CAM"])).toEqual(["FWD", "CAM", "CM"]);
  });

  it("openEligibleHighlights_returnsOpenSlots", () => {
    const open = openEligibleHighlights("FOUR_FOUR_TWO", [], ["FWD", "CM"]);
    expect(open).toContain("FWD");
    expect(open).toContain("CM");
  });
});

export {};
