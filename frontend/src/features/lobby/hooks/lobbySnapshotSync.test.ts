import {
  LOBBY_SNAPSHOT_REFETCH_EVENTS,
  shouldPollLobbyPhase,
  shouldRefetchLobbySnapshot,
} from "./lobbySnapshotSync";
import { RealtimeEnvelope } from "../../../shared/realtime/types";

function env(eventType: string): RealtimeEnvelope {
  return { eventType, occurredAt: "2026-01-01T00:00:00Z", payload: {} };
}

describe("lobbySnapshotSync", () => {
  it("refetches when any unseen event is lobby-relevant", () => {
    expect(shouldRefetchLobbySnapshot([env("formation_chosen")])).toBe(true);
    expect(shouldRefetchLobbySnapshot([env("auctioning_started")])).toBe(true);
    expect(shouldRefetchLobbySnapshot([env("heartbeat")])).toBe(false);
  });

  it("refetches if auctioning_started is not the last event in batch", () => {
    expect(
      shouldRefetchLobbySnapshot([
        env("auctioning_started"),
        env("formation_ready"),
      ])
    ).toBe(true);
  });

  it("polls only during pre-auction lobby phases", () => {
    expect(shouldPollLobbyPhase("FORMATION_SELECT")).toBe(true);
    expect(shouldPollLobbyPhase("AUCTIONING")).toBe(false);
  });

  it("covers all documented refetch event types", () => {
    LOBBY_SNAPSHOT_REFETCH_EVENTS.forEach((type) => {
      expect(shouldRefetchLobbySnapshot([env(type)])).toBe(true);
    });
  });
});
