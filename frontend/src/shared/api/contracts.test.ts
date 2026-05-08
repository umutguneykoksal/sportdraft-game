import { Formation } from "./contracts";

describe("contracts", () => {
  it("keeps formation union values", () => {
    const formation: Formation = "FOUR_FOUR_TWO";
    expect(formation).toBe("FOUR_FOUR_TWO");
  });
});
