import type { SessionSnapshotResponse } from "./api/contracts";

/** Must match LobbyService.LOBBY_PAIRING_COUNTDOWN_SECONDS */
export const LOBBY_PAIRING_COUNTDOWN_SECONDS = 5;

/** Must match LobbyService.FORMATION_FIRST_READY_COUNTDOWN_SECONDS */
export const FORMATION_FIRST_READY_COUNTDOWN_SECONDS = 10;

export type LobbyCountdownPhase = "PAIRING" | "FORMATION_AUCTION_START";

export function inferCountdownPhase(
  snapshot: SessionSnapshotResponse | null | undefined
): LobbyCountdownPhase | null {
  if (!snapshot?.countdownEndsAt) return null;
  if (snapshot.countdownPhase) return snapshot.countdownPhase;
  if (snapshot.status === "COUNTDOWN") return "PAIRING";
  if (snapshot.status === "FORMATION_SELECT") return "FORMATION_AUCTION_START";
  return null;
}

/**
 * Deadline for the active lobby countdown. Uses server {@link countdownPhase}
 * so stale {@link matchStartDeadlineAt} cannot override pairing (5s).
 */
export function resolveLobbyCountdownDeadline(
  snapshot: SessionSnapshotResponse | null | undefined
): string | null {
  if (!snapshot?.countdownEndsAt) return null;

  const phase = inferCountdownPhase(snapshot);
  if (phase === "PAIRING" || phase === "FORMATION_AUCTION_START") {
    return snapshot.countdownEndsAt;
  }

  if (snapshot.status === "MATCH_START_PENDING") {
    return snapshot.matchStartDeadlineAt ?? snapshot.countdownEndsAt ?? null;
  }

  return null;
}

export function computeCountdownSecondsLeft(deadlineAt: string | null): number | null {
  if (!deadlineAt) return null;
  const ms = new Date(deadlineAt).getTime() - Date.now();
  if (Number.isNaN(ms)) return 0;
  return Math.max(0, Math.ceil(ms / 1000));
}
