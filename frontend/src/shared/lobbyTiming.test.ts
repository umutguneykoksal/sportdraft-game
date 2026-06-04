import {
  computeCountdownSecondsLeft,
  inferCountdownPhase,
  resolveLobbyCountdownDeadline,
} from "./lobbyTiming";
import type { SessionSnapshotResponse } from "./api/contracts";

const base: SessionSnapshotResponse = {
  sessionId: "s1",
  status: "COUNTDOWN",
  countdownEndsAt: "2026-01-01T00:00:05.000Z",
  playerCount: 2,
  joined: true,
  myFormation: null,
  matchStartDeadlineAt: "2026-01-01T00:00:10.000Z",
  matchStartReadyCount: 0,
  matchStartConfirmedByMe: false,
};

describe("lobbyTiming", () => {
  it("infersPairingPhaseFromServerField", () => {
    expect(
      inferCountdownPhase({ ...base, countdownPhase: "PAIRING" })
    ).toBe("PAIRING");
  });

  it("prefersCountdownStatusOverStaleFormationPhase", () => {
    expect(
      inferCountdownPhase({
        ...base,
        status: "COUNTDOWN",
        countdownPhase: "FORMATION_AUCTION_START",
        countdownEndsAt: "2026-01-01T00:00:10.000Z",
      })
    ).toBe("PAIRING");
  });

  it("usesOnlyCountdownEndsAtDuringPairingDespiteStaleMatchStartDeadline", () => {
    expect(resolveLobbyCountdownDeadline({ ...base, countdownPhase: "PAIRING" })).toBe(
      "2026-01-01T00:00:05.000Z"
    );
  });

  it("ignoresMatchStartDeadlineWhenCountdownPhaseIsPairing", () => {
    expect(
      resolveLobbyCountdownDeadline({
        ...base,
        countdownPhase: "PAIRING",
        countdownEndsAt: "2026-01-01T00:00:05.000Z",
        matchStartDeadlineAt: "2026-01-01T00:00:10.000Z",
      })
    ).toBe("2026-01-01T00:00:05.000Z");
  });

  it("noFormationCountdownBeforeFirstReady", () => {
    expect(
      inferCountdownPhase({
        ...base,
        status: "FORMATION_SELECT",
        countdownEndsAt: "2026-01-01T00:00:10.000Z",
        matchStartReadyCount: 0,
      })
    ).toBeNull();
    expect(
      resolveLobbyCountdownDeadline({
        ...base,
        status: "FORMATION_SELECT",
        countdownEndsAt: "2026-01-01T00:00:10.000Z",
        matchStartReadyCount: 0,
      })
    ).toBeNull();
  });

  it("usesFormationPhaseDeadlineOnlyAfterFirstReady", () => {
    expect(
      resolveLobbyCountdownDeadline({
        ...base,
        status: "FORMATION_SELECT",
        countdownPhase: "FORMATION_AUCTION_START",
        countdownEndsAt: "2026-01-01T00:00:10.000Z",
        matchStartReadyCount: 1,
        matchStartDeadlineAt: null,
      })
    ).toBe("2026-01-01T00:00:10.000Z");
  });

  it("capsPairingDeadlineToFiveSecondsFromNow", () => {
    const far = new Date(Date.now() + 60_000).toISOString();
    const resolved = resolveLobbyCountdownDeadline({
      ...base,
      status: "COUNTDOWN",
      countdownEndsAt: far,
    });
    expect(resolved).not.toBeNull();
    const seconds = computeCountdownSecondsLeft(resolved);
    expect(seconds).not.toBeNull();
    expect(seconds!).toBeLessThanOrEqual(5);
  });

  it("computeCountdownSecondsLeftReturnsNullForMissingDeadline", () => {
    expect(computeCountdownSecondsLeft(null)).toBeNull();
  });
});

export {};
