import {
  computeCountdownSecondsLeft,
  inferCountdownPhase,
  latchStaleCountdownDeadline,
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

  it("noFormationCountdownBeforeAllReady", () => {
    expect(
      inferCountdownPhase({
        ...base,
        status: "FORMATION_SELECT",
        countdownEndsAt: "2026-01-01T00:00:05.000Z",
        matchStartReadyCount: 0,
        playerCount: 2,
        maxPlayers: 2,
      })
    ).toBeNull();
    expect(
      inferCountdownPhase({
        ...base,
        status: "FORMATION_SELECT",
        countdownEndsAt: "2026-01-01T00:00:05.000Z",
        matchStartReadyCount: 1,
        playerCount: 2,
        maxPlayers: 2,
      })
    ).toBeNull();
    expect(
      resolveLobbyCountdownDeadline({
        ...base,
        status: "FORMATION_SELECT",
        countdownEndsAt: "2026-01-01T00:00:05.000Z",
        matchStartReadyCount: 1,
        playerCount: 2,
        maxPlayers: 2,
      })
    ).toBeNull();
  });

  it("noFormationCountdownUntilFourReadyInTournament", () => {
    expect(
      inferCountdownPhase({
        ...base,
        status: "FORMATION_SELECT",
        gameMode: "TOURNAMENT",
        countdownEndsAt: "2026-01-01T00:00:05.000Z",
        matchStartReadyCount: 3,
        playerCount: 4,
        maxPlayers: 4,
      })
    ).toBeNull();
    expect(
      inferCountdownPhase({
        ...base,
        status: "FORMATION_SELECT",
        gameMode: "TOURNAMENT",
        countdownEndsAt: "2026-01-01T00:00:05.000Z",
        matchStartReadyCount: 4,
        playerCount: 4,
        maxPlayers: 4,
      })
    ).toBe("FORMATION_AUCTION_START");
  });

  it("usesFormationPhaseDeadlineOnlyAfterAllReady", () => {
    expect(
      resolveLobbyCountdownDeadline({
        ...base,
        status: "FORMATION_SELECT",
        countdownPhase: "FORMATION_AUCTION_START",
        countdownEndsAt: "2026-01-01T00:00:05.000Z",
        matchStartReadyCount: 2,
        playerCount: 2,
        maxPlayers: 2,
        matchStartDeadlineAt: null,
      })
    ).toBe("2026-01-01T00:00:05.000Z");
  });

  it("latchStaleCountdownDeadlineFixesOverLongPairingOnce", () => {
    const now = 1_700_000_000_000;
    const far = new Date(now + 60_000).toISOString();
    const latched = latchStaleCountdownDeadline(far, 5, now);
    expect(new Date(latched).getTime()).toBe(now + 5_000);
    expect(Math.ceil((new Date(latched).getTime() - (now + 2_000)) / 1000)).toBe(3);
  });

  it("latchStaleCountdownDeadlineLeavesValidDeadlineUntouched", () => {
    const now = 1_700_000_000_000;
    const ok = new Date(now + 4_000).toISOString();
    expect(latchStaleCountdownDeadline(ok, 5, now)).toBe(ok);
  });

  it("computeCountdownSecondsLeftReturnsNullForMissingDeadline", () => {
    expect(computeCountdownSecondsLeft(null)).toBeNull();
  });
});

export {};
