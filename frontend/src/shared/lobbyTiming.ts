import type { SessionSnapshotResponse } from "./api/contracts";

/** Must match LobbyService.LOBBY_PAIRING_COUNTDOWN_SECONDS */
export const LOBBY_PAIRING_COUNTDOWN_SECONDS = 5;

/** Must match LobbyService.FORMATION_ALL_READY_COUNTDOWN_SECONDS */
export const FORMATION_ALL_READY_COUNTDOWN_SECONDS = 5;

/** @deprecated Use {@link FORMATION_ALL_READY_COUNTDOWN_SECONDS} */
export const FORMATION_FIRST_READY_COUNTDOWN_SECONDS = FORMATION_ALL_READY_COUNTDOWN_SECONDS;

export function formationCountdownReadyThreshold(
  snapshot: SessionSnapshotResponse | null | undefined
): number {
  return snapshot?.maxPlayers ?? snapshot?.playerCount ?? 2;
}

export type LobbyCountdownPhase = "PAIRING" | "FORMATION_AUCTION_START";

/**
 * Status is authoritative over {@link countdownPhase} so stale wire fields cannot
 * show an auction timer during the 5s pairing countdown.
 */
export function inferCountdownPhase(
  snapshot: SessionSnapshotResponse | null | undefined
): LobbyCountdownPhase | null {
  if (!snapshot?.countdownEndsAt) return null;

  if (snapshot.status === "COUNTDOWN") {
    return "PAIRING";
  }

  if (snapshot.status === "FORMATION_SELECT") {
    if ((snapshot.matchStartReadyCount ?? 0) < formationCountdownReadyThreshold(snapshot)) {
      return null;
    }
    return "FORMATION_AUCTION_START";
  }

  if (snapshot.countdownPhase) {
    return snapshot.countdownPhase;
  }

  return null;
}

/** Max seconds for the active phase (null when no countdown). */
export function maxCountdownSecondsForPhase(
  phase: LobbyCountdownPhase | null
): number | null {
  if (phase === "PAIRING") return LOBBY_PAIRING_COUNTDOWN_SECONDS;
  if (phase === "FORMATION_AUCTION_START") return FORMATION_ALL_READY_COUNTDOWN_SECONDS;
  return null;
}

/**
 * One-shot correction when wire {@link countdownEndsAt} is too far in the future.
 * Must be latched (same {@link nowMs}) — do not call every tick with a moving clock.
 */
export function latchStaleCountdownDeadline(
  endsAt: string,
  maxSeconds: number,
  nowMs: number
): string {
  const endsMs = new Date(endsAt).getTime();
  if (Number.isNaN(endsMs)) return endsAt;
  const maxMs = maxSeconds * 1000;
  const remainingMs = endsMs - nowMs;
  if (remainingMs <= maxMs) return endsAt;
  return new Date(nowMs + maxMs).toISOString();
}

/**
 * Deadline for the active lobby countdown. Never uses {@link matchStartDeadlineAt}
 * except in {@link MATCH_START_PENDING}. Trust server {@link countdownEndsAt}; stale
 * over-long deadlines are corrected once in {@link useLobbyCountdownSeconds}.
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

  return snapshot.countdownEndsAt;
}

export function countdownLatchKey(
  snapshot: SessionSnapshotResponse | null | undefined
): string | null {
  const phase = inferCountdownPhase(snapshot);
  if (!snapshot?.sessionId || !snapshot.countdownEndsAt || !phase) return null;
  return `${snapshot.sessionId}|${snapshot.countdownEndsAt}|${phase}`;
}

export function computeCountdownSecondsLeft(deadlineAt: string | null): number | null {
  if (!deadlineAt) return null;
  const ms = new Date(deadlineAt).getTime() - Date.now();
  if (Number.isNaN(ms)) return 0;
  return Math.max(0, Math.ceil(ms / 1000));
}
