import { useCallback, useEffect, useRef, useState } from "react";
import { AuctionRoundResponse, AuctionStateResponse, SessionSnapshotResponse } from "../api/contracts";
import { getAuctionState, getSessionSnapshot } from "../api";
import { fetchEventsSince } from "../api/sessionClient";
import { RealtimeEnvelope } from "./types";
import { useRealtimeSession } from "./useRealtimeSession";

export interface SessionSyncState {
  snapshot: SessionSnapshotResponse | null;
  auctionState: AuctionStateResponse | null;
  round: AuctionRoundResponse | null;
  /**
   * Allows handlers to apply an optimistic round update (e.g. after placeBid response).
   * Will be overwritten by the next in-order push event.
   */
  setRound: (r: AuctionRoundResponse | null) => void;
  isConnected: boolean;
  /** Last sequenced event received; consumers may inspect for navigation/side-effects. */
  lastEvent: RealtimeEnvelope | null;
  error: string | null;
}

/**
 * Central sync hook: push-first with seq-based gap detection.
 *
 * Sync rules:
 *  - On connect/reconnect → fetch full snapshot + auction state, update lastSeq.
 *  - On push event:
 *      - seq === 0 (heartbeat/unsequenced) → ignore
 *      - seq === lastSeq + 1 → apply patch, advance lastSeq
 *      - seq > lastSeq + 1  → gap detected, fetch /events/sync?afterSeq=lastSeq
 *      - seq <= lastSeq     → duplicate, ignore
 *  - Patch-first events (bid_placed / round_started / turn_passed) → apply round from payload, no GET.
 *  - round_ended → apply round from payload + fetch auction state (teams/budgets).
 *  - Phase-transition events → fetch snapshot.
 *  - Disconnected fallback poll → 5 s; connected poll disabled.
 */
export function useSessionSync(sessionId: string | null, spectator = false): SessionSyncState {
  const [snapshot, setSnapshot] = useState<SessionSnapshotResponse | null>(null);
  const [auctionState, setAuctionState] = useState<AuctionStateResponse | null>(null);
  const [round, setRound] = useState<AuctionRoundResponse | null>(null);
  const [lastEvent, setLastEvent] = useState<RealtimeEnvelope | null>(null);
  const [error, setError] = useState<string | null>(null);

  const { events, isConnected } = useRealtimeSession(sessionId, spectator);

  const lastSeqRef = useRef<number>(0);
  const syncSeqRef = useRef(0);
  const lastProcessedEventIdxRef = useRef(-1);

  // --- Full refresh (initial + reconnect) ---
  const refresh = useCallback(async (sid: string, seq?: number) => {
    const localSeq = ++syncSeqRef.current;
    try {
      const [snap, state] = await Promise.all([
        getSessionSnapshot(sid),
        getAuctionState(sid),
      ]);
      if (localSeq !== syncSeqRef.current) return;
      setSnapshot(snap);
      setAuctionState(state);
      setRound(state.currentRound);
      setError(null);
      // Update lastSeq from snapshot if available and no explicit seq given.
      if (seq !== undefined) {
        lastSeqRef.current = seq;
      } else if (snap.lastSeq != null) {
        lastSeqRef.current = snap.lastSeq;
      }
    } catch (e) {
      if (localSeq === syncSeqRef.current) {
        setError(e instanceof Error ? e.message : "Sync failed");
      }
    }
  }, []);

  // --- Gap recovery via event log ---
  const recoverGap = useCallback(async (sid: string, afterSeq: number) => {
    try {
      const { events: missed, lastSeq } = await fetchEventsSince(sid, afterSeq);
      if (missed.length === 0) {
        // Gap is beyond ring buffer or no new events; fall back to full snapshot.
        await refresh(sid);
        return;
      }
      // Apply each missed event in order.
      for (const raw of missed) {
        const env = raw as RealtimeEnvelope;
        applyEventPatch(env, setRound, setSnapshot, setAuctionState);
        setLastEvent(env);
      }
      lastSeqRef.current = lastSeq;
    } catch {
      // Recovery failed; fall back to full snapshot.
      await refresh(sid);
    }
  }, [refresh]);

  // --- Initial load ---
  useEffect(() => {
    if (!sessionId) return;
    refresh(sessionId);
  }, [sessionId, refresh]);

  // Reconnect catch-up is handled implicitly by gap recovery (seq gap > 1 → recoverGap).
  // Tracking prevConnected here is not needed.

  // --- Push event handler ---
  useEffect(() => {
    if (!sessionId || events.length === 0) return;
    const idx = events.length - 1;
    if (idx <= lastProcessedEventIdxRef.current) return;
    lastProcessedEventIdxRef.current = idx;

    const env = events[idx];
    const seq = env.seq ?? 0;

    // Heartbeat / unsequenced → no-op
    if (seq === 0 || env.eventType === "heartbeat") return;

    // Duplicate
    if (seq <= lastSeqRef.current) return;

    // Gap detected
    if (seq > lastSeqRef.current + 1) {
      recoverGap(sessionId, lastSeqRef.current);
      return;
    }

    // In-order: apply patch
    lastSeqRef.current = seq;
    applyEventPatch(env, setRound, setSnapshot, setAuctionState);
    setLastEvent(env);

    // round_ended: also refresh teams/budgets
    if (env.eventType === "round_ended") {
      const localSeq = ++syncSeqRef.current;
      getAuctionState(sessionId).then((state) => {
        if (localSeq !== syncSeqRef.current) return;
        setAuctionState(state);
        setRound(state.currentRound);
      }).catch(() => {});
    }

    // Phase transitions: refresh full snapshot
    if (
      env.eventType === "match_start_pending" ||
      env.eventType === "match_start_ready" ||
      env.eventType === "session_finalizing" ||
      env.eventType === "auctioning_started" ||
      env.eventType === "formation_phase_started" ||
      env.eventType === "lobby_updated" ||
      env.eventType === "countdown_started" ||
      env.eventType === "countdown_cancelled" ||
      env.eventType === "formation_ready"
    ) {
      const localSeq = ++syncSeqRef.current;
      getSessionSnapshot(sessionId).then((snap) => {
        if (localSeq !== syncSeqRef.current) return;
        setSnapshot(snap);
      }).catch(() => {});
    }
  }, [events, sessionId, recoverGap]);

  // --- Disconnected fallback poll (5 s) ---
  useEffect(() => {
    if (!sessionId || isConnected) return;
    const id = window.setInterval(() => refresh(sessionId), 5000);
    return () => window.clearInterval(id);
  }, [sessionId, isConnected, refresh]);

  return { snapshot, auctionState, round, setRound, isConnected, lastEvent, error };
}

// --- Patch helpers ---

type RoundSetter = (r: AuctionRoundResponse | null) => void;
type SnapSetter = (s: SessionSnapshotResponse | null) => void;
type StateSetter = (s: AuctionStateResponse | null) => void;

function applyEventPatch(
  env: RealtimeEnvelope,
  setRound: RoundSetter,
  _setSnapshot: SnapSetter,
  _setAuctionState: StateSetter,
): void {
  const payload = env.payload as Record<string, unknown>;

  // Patch round from payload directly (no extra GET)
  if (
    env.eventType === "bid_placed" ||
    env.eventType === "round_started" ||
    env.eventType === "turn_passed" ||
    env.eventType === "round_ended" ||
    env.eventType === "round_ready"
  ) {
    if (payload.round) {
      setRound(payload.round as AuctionRoundResponse);
    }
  }
}
