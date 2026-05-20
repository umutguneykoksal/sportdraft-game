import { RealtimeEnvelope } from "../../../shared/realtime/types";

/** SSE types that change lobby snapshot (status, ready count, countdown). */
export const LOBBY_SNAPSHOT_REFETCH_EVENTS = [
  "lobby_updated",
  "countdown_started",
  "countdown_cancelled",
  "formation_phase_started",
  "formation_chosen",
  "formation_ready",
  "auctioning_started",
] as const;

export const LOBBY_PHASE_POLL_STATUSES = [
  "WAITING",
  "COUNTDOWN",
  "FORMATION_SELECT",
] as const;

export function shouldRefetchLobbySnapshot(envs: RealtimeEnvelope[]): boolean {
  return envs.some((e) =>
    (LOBBY_SNAPSHOT_REFETCH_EVENTS as readonly string[]).includes(e.eventType)
  );
}

export function shouldPollLobbyPhase(status: string | undefined): boolean {
  return (
    status != null &&
    (LOBBY_PHASE_POLL_STATUSES as readonly string[]).includes(status)
  );
}
