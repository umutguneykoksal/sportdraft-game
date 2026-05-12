import { useEffect, useRef, useState } from "react";
import { AuctionRoundResponse, SessionSnapshotResponse, getAuctionState, getSessionSnapshot } from "../../../shared/api";
import { useSessionStream } from "../../../shared/sse";

export function useAuctionOrchestrator(sessionId: string | null) {
  const [round, setRound] = useState<AuctionRoundResponse | null>(null);
  const [snapshot, setSnapshot] = useState<SessionSnapshotResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { events, isConnected } = useSessionStream(sessionId);
  const lastProcessedEventIdx = useRef(-1);
  const prevConnectedRef = useRef(false);

  const refresh = (sid: string) =>
    Promise.all([getSessionSnapshot(sid), getAuctionState(sid)])
      .then(([snap, state]) => {
        setSnapshot(snap);
        setRound(state.currentRound);
      })
      .catch((e) => setError(e instanceof Error ? e.message : "Auction sync failed"));

  // 1. Initial fetch on mount
  useEffect(() => {
    if (!sessionId) return;
    refresh(sessionId);
  }, [sessionId]);

  // 2. SSE reconnection catch-up
  useEffect(() => {
    const wasConnected = prevConnectedRef.current;
    prevConnectedRef.current = isConnected;
    if (!sessionId || !isConnected || !wasConnected) return;
    refresh(sessionId);
  }, [sessionId, isConnected]);

  // 3. SSE event handler (only refetch on meaningful events, deduplicated)
  useEffect(() => {
    if (!sessionId || events.length === 0) return;
    const idx = events.length - 1;
    if (idx <= lastProcessedEventIdx.current) return;
    lastProcessedEventIdx.current = idx;

    const latest = events[idx];
    const refetchEvents = [
      "round_started", "round_ended", "bid_placed", "round_skipped",
      "session_finalizing", "match_completed", "match_start_pending",
      "match_start_ready", "auctioning_started",
    ];
    if (refetchEvents.includes(latest.eventType)) {
      refresh(sessionId);
    }
  }, [sessionId, events]);

  return { round, snapshot, events, isConnected, error };
}
