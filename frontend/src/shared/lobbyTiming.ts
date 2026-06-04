import type { SessionSnapshotResponse } from "./api/contracts";

/** Must match LobbyService.LOBBY_PAIRING_COUNTDOWN_SECONDS */
export const LOBBY_PAIRING_COUNTDOWN_SECONDS = 5;

/** Must match LobbyService.FORMATION_FIRST_READY_COUNTDOWN_SECONDS */
export const FORMATION_FIRST_READY_COUNTDOWN_SECONDS = 10;

export type LobbyCountdownPhase = "PAIRING" | "FORMATION_AUCTION_START";

/**
 * Status is authoritative over {@link countdownPhase} so stale wire fields cannot
 * show a 10s auction timer during the 5s pairing countdown.
 */
export function inferCountdownPhase(
  snapshot: SessionSnapshotResponse | null | undefined
): LobbyCountdownPhase | null {
  if (!snapshot?.countdownEndsAt) return null;

  if (snapshot.status === "COUNTDOWN") {
    return "PAIRING";
  }

  if (snapshot.status === "FORMATION_SELECT") {
    if ((snapshot.matchStartReadyCount ?? 0) < 1) {
      return null;
    }
    return "FORMATION_AUCTION_START";
  }

  if (snapshot.countdownPhase) {
    return snapshot.countdownPhase;
  }

  return null;
}

function capDeadlineIso(endsAt: string, capSeconds: number): string {
  const endsMs = new Date(endsAt).getTime();
  if (Number.isNaN(endsMs)) return endsAt;
  const capMs = Date.now() + capSeconds * 1000;
  return new Date(Math.min(endsMs, capMs)).toISOString();
}

/**
 * Deadline for the active lobby countdown. Never uses {@link matchStartDeadlineAt}
 * except in {@link MATCH_START_PENDING}.
 */
export function resolveLobbyCountdownDeadline(
  snapshot: SessionSnapshotResponse | null | undefined
): string | null {
  const phase = inferCountdownPhase(snapshot);
  if (!phase || !snapshot?.countdownEndsAt) {
    if (snapshot?.status === "MATCH_START_PENDING") {
      return snapshot.matchStartDeadlineAt ?? snapshot.countdownEndsAt ?? null;
    }
    return null;
  }

  if (phase === "PAIRING") {
    return capDeadlineIso(snapshot.countdownEndsAt, LOBBY_PAIRING_COUNTDOWN_SECONDS);
  }

  return capDeadlineIso(snapshot.countdownEndsAt, FORMATION_FIRST_READY_COUNTDOWN_SECONDS);
}

export function computeCountdownSecondsLeft(deadlineAt: string | null): number | null {
  if (!deadlineAt) return null;
  const ms = new Date(deadlineAt).getTime() - Date.now();
  if (Number.isNaN(ms)) return 0;
  return Math.max(0, Math.ceil(ms / 1000));
}
