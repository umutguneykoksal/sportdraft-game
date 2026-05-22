import {
  computeDisplaySecondsLeft,
  isInCardRevealPhase,
  ROUND_BID_SECONDS,
  ROUND_REVEAL_SECONDS,
} from "./auctionTiming";

describe("auctionTiming", () => {
  it("shows full bid clock during reveal", () => {
    const startsAt = new Date("2026-01-01T12:00:00.000Z").toISOString();
    const endsAt = new Date("2026-01-01T12:00:32.000Z").toISOString();
    const duringReveal = Date.parse("2026-01-01T12:00:01.000Z") - Date.now();

    expect(isInCardRevealPhase(startsAt, endsAt, duringReveal)).toBe(true);
    expect(computeDisplaySecondsLeft(startsAt, endsAt, duringReveal)).toBe(ROUND_BID_SECONDS);
  });

  it("infers startsAt for first card when API omits it", () => {
    const endsAt = new Date(Date.now() + 31_000).toISOString();
    const duringReveal = 0;

    expect(isInCardRevealPhase(null, endsAt, duringReveal)).toBe(true);
    expect(computeDisplaySecondsLeft(null, endsAt, duringReveal)).toBe(ROUND_BID_SECONDS);
  });

  it("tracks server bid time after reveal", () => {
    const startsAt = new Date("2026-01-01T12:00:00.000Z").toISOString();
    const endsAt = new Date("2026-01-01T12:00:32.000Z").toISOString();
    const afterReveal = Date.parse("2026-01-01T12:00:02.500Z") - Date.now();

    expect(isInCardRevealPhase(startsAt, endsAt, afterReveal)).toBe(false);
    expect(computeDisplaySecondsLeft(startsAt, endsAt, afterReveal)).toBe(30);
  });

  it("uses configured reveal length", () => {
    expect(ROUND_REVEAL_SECONDS).toBe(2);
  });
});
