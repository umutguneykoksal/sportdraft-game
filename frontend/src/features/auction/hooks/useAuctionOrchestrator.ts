import { useEffect, useState } from "react";
import { AuctionRoundResponse, SessionSnapshotResponse, getAuctionState, getSessionSnapshot } from "../../../shared/api";
import { useSessionStream } from "../../../shared/sse";

export function useAuctionOrchestrator(sessionId: string | null) {
  const [round, setRound] = useState<AuctionRoundResponse | null>(null);
  const [snapshot, setSnapshot] = useState<SessionSnapshotResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { events, isConnected } = useSessionStream(sessionId);

  useEffect(() => {
    if (!sessionId) {
      return;
    }
    let cancelled = false;
    (async () => {
      try {
        const [snap, state] = await Promise.all([getSessionSnapshot(sessionId), getAuctionState(sessionId)]);
        if (cancelled) {
          return;
        }
        setSnapshot(snap);
        setRound(state.currentRound);
      } catch (e) {
        if (!cancelled) {
          setError(e instanceof Error ? e.message : "Auction sync failed");
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [sessionId, events, isConnected]);

  return { round, snapshot, events, isConnected, error };
}
