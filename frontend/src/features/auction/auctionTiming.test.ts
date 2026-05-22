import {
  computeDisplaySecondsLeft,
  computeServerSecondsLeft,
  isInCardRevealPhase,
  resolveRoundStartsAt,
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

  it("does not infer reveal when endsAt is far out (anti-snipe extension)", () => {
    const endsAt = new Date(Date.now() + 60_000).toISOString();
    expect(isInCardRevealPhase(null, endsAt, 0)).toBe(false);
  });

  it("uses configured reveal length", () => {
    expect(ROUND_REVEAL_SECONDS).toBe(2);
  });

  it("returns explicit startsAt when provided", () => {
    const startsAt = "2026-01-01T12:00:00.000Z";
    const endsAt = "2026-01-01T12:00:32.000Z";
    expect(resolveRoundStartsAt(startsAt, endsAt)).toBe(startsAt);
  });

  it("returns null when endsAt cannot be parsed", () => {
    expect(resolveRoundStartsAt(null, "not-a-date", 0)).toBeNull();
  });

  it("computeServerSecondsLeft never goes below zero", () => {
    const endsAt = new Date(Date.now() - 5000).toISOString();
    expect(computeServerSecondsLeft(endsAt, 0)).toBe(0);
  });
});
