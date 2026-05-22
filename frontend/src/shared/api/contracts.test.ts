import { Formation, poolLeagueLabel } from "./contracts";

describe("contracts", () => {
  it("keeps formation union values", () => {
    const formation: Formation = "FOUR_FOUR_TWO";
    expect(formation).toBe("FOUR_FOUR_TWO");
  });

  it("poolLeagueLabel_returnsKnownLeague", () => {
    expect(poolLeagueLabel("SUPER_LIG")).toBe("Trendyol Super Lig");
  });

  it("poolLeagueLabel_fallsBackToDefault", () => {
    expect(poolLeagueLabel(null)).toBe("Premier League");
  });
});
