import { resolveLobbyCountdownDeadline } from "./lobbyTiming";
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

describe("resolveLobbyCountdownDeadline", () => {
  it("usesOnlyCountdownEndsAtDuringPairingCountdown", () => {
    expect(resolveLobbyCountdownDeadline(base)).toBe("2026-01-01T00:00:05.000Z");
  });

  it("ignoresMatchStartDeadlineDuringFormationSelect", () => {
    expect(
      resolveLobbyCountdownDeadline({
        ...base,
        status: "FORMATION_SELECT",
        countdownEndsAt: null,
        matchStartDeadlineAt: "2026-01-01T00:00:10.000Z",
      })
    ).toBeNull();
  });

  it("usesCountdownEndsAtDuringFormationAuctionStart", () => {
    expect(
      resolveLobbyCountdownDeadline({
        ...base,
        status: "FORMATION_SELECT",
        countdownEndsAt: "2026-01-01T00:00:10.000Z",
        matchStartDeadlineAt: "2026-01-01T00:00:10.000Z",
      })
    ).toBe("2026-01-01T00:00:10.000Z");
  });
});

export {};
