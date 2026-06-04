import type { SessionSnapshotResponse } from "./api/contracts";

/** Must match LobbyService.LOBBY_PAIRING_COUNTDOWN_SECONDS */
export const LOBBY_PAIRING_COUNTDOWN_SECONDS = 5;

/** Must match LobbyService.FORMATION_FIRST_READY_COUNTDOWN_SECONDS */
export const FORMATION_FIRST_READY_COUNTDOWN_SECONDS = 10;

/**
 * Single deadline source per phase — avoids mixing countdownEndsAt with
 * matchStartDeadlineAt (backend mirrors the latter during FORMATION_SELECT).
 */
export function resolveLobbyCountdownDeadline(
  snapshot: SessionSnapshotResponse | null | undefined
): string | null {
  if (!snapshot) return null;

  switch (snapshot.status) {
    case "COUNTDOWN":
      return snapshot.countdownEndsAt ?? null;
    case "FORMATION_SELECT":
      return snapshot.countdownEndsAt ?? null;
    case "MATCH_START_PENDING":
      return snapshot.matchStartDeadlineAt ?? snapshot.countdownEndsAt ?? null;
    default:
      return null;
  }
}
