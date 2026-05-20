import { AuctionRoundResponse } from "../api/contracts";
import { RealtimeEnvelope } from "./types";
import { MAX_SEEN_EVENT_IDS } from "./constants";

export type RefreshKind = "none" | "state" | "full";

/** Fields from SSE round payload safe to merge into local round state. */
export const SHARED_ROUND_FIELDS: ReadonlyArray<keyof AuctionRoundResponse> = [
  "highestBidCents",
  "highestBidderUserId",
  "currentTurnUserId",
  "endsAt",
  "turnOpenAt",
  "passedPlayerIds",
  "status",
  "opponentCanBid",
];

export function pickSharedFields(src: AuctionRoundResponse): Partial<AuctionRoundResponse> {
  const srcAny = src as unknown as Record<string, unknown>;
  const out: Record<string, unknown> = {};
  for (const k of SHARED_ROUND_FIELDS) {
    if (k in src) out[k] = srcAny[k];
  }
  return out as Partial<AuctionRoundResponse>;
}

/**
 * Apply REST-fetched round only when it is not older than the current in-memory round.
 * Prevents in-flight poll responses from stomping a fresher SSE patch.
 */
export function mergeRoundFromRest(
  current: AuctionRoundResponse | null,
  incoming: AuctionRoundResponse | null
): AuctionRoundResponse | null {
  if (incoming == null) return null;
  if (current == null) return incoming;
  if (current.roundId !== incoming.roundId) return incoming;

  if (incoming.version != null && current.version != null && incoming.version < current.version) {
    return current;
  }

  const currentBid = current.highestBidCents ?? 0;
  const incomingBid = incoming.highestBidCents ?? 0;
  if (incomingBid < currentBid) return current;

  if (incomingBid === currentBid && incoming.endsAt && current.endsAt) {
    const incomingEnds = new Date(incoming.endsAt).getTime();
    const currentEnds = new Date(current.endsAt).getTime();
    if (incomingEnds < currentEnds) return current;
  }

  return incoming;
}

export type ReplayDecision = "none" | "replay" | "full";

/** Decide reconnect replay strategy from sync API response. */
export function decideReplayAction(
  lastSeq: number,
  serverLastSeq: number,
  missedCount: number
): ReplayDecision {
  if (serverLastSeq < lastSeq) return "full";
  if (missedCount > 0) return "replay";
  if (serverLastSeq > lastSeq) return "full";
  return "none";
}

export interface DispatchResult {
  refreshKind: RefreshKind;
  matchCompleted: boolean;
  seqGapDetected: boolean;
}

/**
 * Process realtime envelopes and return what REST refresh (if any) is needed.
 * Caller applies round patches via returned side effects in callbacks.
 */
export function processEventBatch(
  envs: RealtimeEnvelope[],
  lastServerSeq: number,
  handlers: {
    patchRound: (incoming: AuctionRoundResponse, merge: boolean) => void;
    clearRound: () => void;
    setRound: (round: AuctionRoundResponse) => void;
    onMatchCompleted: () => void;
  },
  options?: { spectate?: boolean }
): { result: DispatchResult; lastSeq: number } {
  const spectate = options?.spectate ?? false;
  let refreshKind: RefreshKind = "none";
  let matchCompleted = false;
  let seqGapDetected = false;
  let maxSeq = lastServerSeq;

  for (const env of envs) {
    if (env.eventType === "heartbeat" || (env.seq ?? 0) === 0) {
      continue;
    }

    const seq = env.seq ?? 0;

    if (env.eventType === "match_completed") {
      handlers.onMatchCompleted();
      return { result: { refreshKind: "none", matchCompleted: true, seqGapDetected }, lastSeq: maxSeq };
    }

    switch (env.eventType) {
      case "bid_placed":
      case "turn_passed": {
        if (seq > 0 && maxSeq > 0 && seq > maxSeq + 1) {
          seqGapDetected = true;
        }
        const payload = env.payload as { round?: AuctionRoundResponse };
        if (payload?.round) {
          handlers.patchRound(payload.round, true);
        }
        // Refresh player budgets / rosters (4-player auction state.players[]).
        refreshKind = maxRefresh(refreshKind, "state");
        break;
      }

      case "round_ended": {
        const endedPayload = env.payload as { result?: string };
        if (endedPayload?.result === "SOLD") {
          refreshKind = maxRefresh(refreshKind, spectate ? "full" : "state");
        } else {
          handlers.clearRound();
          // Server auto-starts next round; REST fallback if round_started event is missed.
          refreshKind = maxRefresh(refreshKind, "state");
        }
        break;
      }

      case "round_ready":
      case "match_start_ready":
        refreshKind = maxRefresh(refreshKind, spectate ? "full" : "state");
        break;

      case "round_resolving":
        refreshKind = maxRefresh(refreshKind, "state");
        break;

      case "round_started": {
        const startedPayload = env.payload as { round?: AuctionRoundResponse };
        if (startedPayload?.round) {
          handlers.setRound(startedPayload.round);
        } else {
          refreshKind = maxRefresh(refreshKind, "state");
        }
        break;
      }

      case "match_start_pending":
      case "session_finalizing":
        refreshKind = maxRefresh(refreshKind, "full");
        break;

      default:
        refreshKind = maxRefresh(refreshKind, "state");
        break;
    }

    if (seq > 0) {
      maxSeq = Math.max(maxSeq, seq);
    }
  }

  if (seqGapDetected) {
    refreshKind = maxRefresh(refreshKind, "state");
  }

  return {
    result: { refreshKind, matchCompleted, seqGapDetected },
    lastSeq: maxSeq,
  };
}

/** Sort envelopes by seq ascending; unsequenced events keep stable relative order. */
export function sortBySeq(envs: RealtimeEnvelope[]): RealtimeEnvelope[] {
  return [...envs].sort((a, b) => {
    const seqA = a.seq ?? 0;
    const seqB = b.seq ?? 0;
    if (seqA === 0 && seqB === 0) return 0;
    if (seqA === 0) return 1;
    if (seqB === 0) return -1;
    return seqA - seqB;
  });
}

export function maxRefresh(current: RefreshKind, next: RefreshKind): RefreshKind {
  if (current === "full" || next === "full") return "full";
  if (current === "state" || next === "state") return "state";
  return "none";
}

const MAX_SEEN_EVENT_IDS_CAP = MAX_SEEN_EVENT_IDS;

/** Drain envelopes not yet processed (stable across ring-buffer truncation). */
export function drainUnseenEvents(
  events: RealtimeEnvelope[],
  seen: Set<string>
): RealtimeEnvelope[] {
  const fresh: RealtimeEnvelope[] = [];
  for (const env of events) {
    const id = env.eventId ?? `${env.eventType}:${env.occurredAt}:${env.seq ?? 0}`;
    if (seen.has(id)) continue;
    seen.add(id);
    fresh.push(env);
  }
  while (seen.size > MAX_SEEN_EVENT_IDS_CAP) {
    const oldest = seen.values().next().value;
    if (oldest === undefined) break;
    seen.delete(oldest);
  }
  return fresh;
}
