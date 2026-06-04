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

  it("usesFormationPhaseDeadlineOnly", () => {
    expect(
      resolveLobbyCountdownDeadline({
        ...base,
        status: "FORMATION_SELECT",
        countdownPhase: "FORMATION_AUCTION_START",
        countdownEndsAt: "2026-01-01T00:00:10.000Z",
        matchStartDeadlineAt: null,
      })
    ).toBe("2026-01-01T00:00:10.000Z");
  });

  it("computeCountdownSecondsLeftReturnsNullForMissingDeadline", () => {
    expect(computeCountdownSecondsLeft(null)).toBeNull();
  });
});

export {};
