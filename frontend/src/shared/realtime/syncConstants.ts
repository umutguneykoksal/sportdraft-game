/** Shared REST poll / reconnect intervals for session and spectate sync hooks. */
export const POLL_INTERVAL_IDLE_MS = 30_000;
export const POLL_INTERVAL_ROUND_OPEN_MS = 5_000;
export const POLL_INTERVAL_DISCONNECTED_MS = 2_000;
export const VISIBILITY_REFRESH_THRESHOLD_MS = 10_000;
export const HEARTBEAT_TIMEOUT_MS = 90_000;

/** Poll while an OPEN round is active but game events stall on a live transport. */
export const STALE_GAME_EVENT_MS = 12_000;

/** Spectate match replay timeline poll interval. */
export const MATCH_REPLAY_POLL_MS = 2_000;

/** Player match result poll while simulation is in progress. */
export const MATCH_RESULT_POLL_MS = 2_000;

/** Show a user-visible warning when simulation/replay polling exceeds this duration. */
export const SIMULATION_STALL_WARNING_MS = 120_000;
