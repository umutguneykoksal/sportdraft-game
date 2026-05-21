import { deriveRoundTimerEffects } from "./deriveRoundTimerState";

describe("deriveRoundTimerEffects", () => {
  const baseInput = {
    roundId: "r1",
    endsAt: new Date(Date.now() + 10_000).toISOString(),
    previousRoundId: "r1" as string | null,
    previousSecondsLeft: 8,
    roundTotalSeconds: 10,
    serverClockOffsetMs: 0,
    hasGraceTimeout: false,
  };

  it("starts grace when timer expires without extension", () => {
    const effects = deriveRoundTimerEffects({
      ...baseInput,
      endsAt: new Date(Date.now() - 1_000).toISOString(),
      previousSecondsLeft: 0,
    });

    expect(effects.secondsLeft).toBe(0);
    expect(effects.startGrace).toBe(true);
  });

  it("marks time extended when countdown jumps after expiry", () => {
    const effects = deriveRoundTimerEffects({
      ...baseInput,
      endsAt: new Date(Date.now() + 5_000).toISOString(),
      previousSecondsLeft: 0,
    });

    expect(effects.timeExtended).toBe(true);
    expect(effects.clearGrace).toBe(true);
  });

  it("resets totals when round changes", () => {
    const effects = deriveRoundTimerEffects({
      ...baseInput,
      roundId: "r2",
      previousRoundId: "r1",
      previousSecondsLeft: 0,
    });

    expect(effects.previousRoundId).toBe("r2");
    expect(effects.clearGrace).toBe(true);
  });
});
