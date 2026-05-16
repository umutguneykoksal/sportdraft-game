import { useCallback, useEffect, useRef, useState } from "react";
import { AuctionRoundResponse, AuctionStateResponse, SessionSnapshotResponse } from "../api/contracts";
import { getAuctionState, getSessionSnapshot } from "../api";
import { ApiError } from "../api/errors";
import { RealtimeEnvelope } from "./types";
import { useRealtimeSession } from "./useRealtimeSession";

export interface SessionSyncState {
  snapshot: SessionSnapshotResponse | null;
  auctionState: AuctionStateResponse | null;
  round: AuctionRoundResponse | null;
  setRound: (r: AuctionRoundResponse | null) => void;
  isConnected: boolean;
  lastEvent: RealtimeEnvelope | null;
  error: string | null;
  sessionNotFound: boolean;
}

/**
 * Poll-first, Push-optimization sync strategy.
 *
 * How it works:
 *  1. Poll every POLL_INTERVAL_MS (3s) regardless of push connection
 *  2. When push event arrives → immediate poll (skip waiting)
 *  3. Server version in snapshot → skip redundant updates if unchanged
 *
 * Why this is better than patch-from-push:
 *  - Push is unreliable (network drops, proxy buffering, browser throttling)
 *  - Patching from event payloads can cause state divergence
 *  - Poll always returns authoritative server state
 *  - Push just accelerates the refresh, doesn't carry state
 */

const POLL_INTERVAL_MS = 3000;
const POLL_INTERVAL_DISCONNECTED_MS = 2000;

export function useSessionSync(sessionId: string | null, spectator = false): SessionSyncState {
  const [snapshot, setSnapshot] = useState<SessionSnapshotResponse | null>(null);
  const [auctionState, setAuctionState] = useState<AuctionStateResponse | null>(null);
  const [round, setRound] = useState<AuctionRoundResponse | null>(null);
  const [lastEvent, setLastEvent] = useState<RealtimeEnvelope | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sessionNotFound, setSessionNotFound] = useState(false);

  const { events, isConnected } = useRealtimeSession(sessionId, spectator);

  // Refs for fetch coordination
  const fetchingRef = useRef(false);
  const pendingRefreshRef = useRef(false);
  const lastEventIdxRef = useRef(-1);

  // Reset on session change
  useEffect(() => {
    setSnapshot(null);
    setAuctionState(null);
    setRound(null);
    setLastEvent(null);
    setError(null);
    setSessionNotFound(false);
    fetchingRef.current = false;
    pendingRefreshRef.current = false;
    lastEventIdxRef.current = -1;
  }, [sessionId]);

  // Core refresh function - always fetches full state from server
  const refresh = useCallback(async (sid: string) => {
    // Don't skip if already fetching - queue the intent instead
    if (fetchingRef.current) {
      // Mark that another refresh is needed after current one completes
      pendingRefreshRef.current = true;
      return;
    }
    fetchingRef.current = true;
    pendingRefreshRef.current = false;

    try {
      const [snap, state] = await Promise.all([
        getSessionSnapshot(sid),
        getAuctionState(sid),
      ]);

      // Always update state - no version check that could skip updates
      setSnapshot(snap);
      setAuctionState(state);
      setRound(state.currentRound);
      setError(null);
      setSessionNotFound(false);
    } catch (e) {
      if (e instanceof ApiError && (e.status === 404 || e.status === 401)) {
        setSessionNotFound(true);
        setError(null);
      } else {
        setError(e instanceof Error ? e.message : "Sync failed");
      }
    } finally {
      fetchingRef.current = false;
      
      // If another refresh was requested while we were fetching, do it now
      if (pendingRefreshRef.current) {
        pendingRefreshRef.current = false;
        refresh(sid);
      }
    }
  }, []);

  // Initial load
  useEffect(() => {
    if (!sessionId) return;
    refresh(sessionId);
  }, [sessionId, refresh]);

  // Regular polling - the backbone of reliability
  useEffect(() => {
    if (!sessionId || sessionNotFound) return;
    
    const interval = isConnected ? POLL_INTERVAL_MS : POLL_INTERVAL_DISCONNECTED_MS;
    const id = window.setInterval(() => refresh(sessionId), interval);
    return () => window.clearInterval(id);
  }, [sessionId, isConnected, sessionNotFound, refresh]);

  // Push event handler - triggers immediate refresh, doesn't patch
  useEffect(() => {
    if (!sessionId || events.length === 0 || sessionNotFound) return;

    // Process new events since last check
    for (let i = lastEventIdxRef.current + 1; i < events.length; i++) {
      const env = events[i];
      
      // Skip heartbeats
      if (env.eventType === "heartbeat" || (env.seq ?? 0) === 0) continue;

      setLastEvent(env);

      // Push arrived → immediate refresh (don't wait for poll interval)
      refresh(sessionId);
      break; // One refresh per batch is enough
    }
    
    lastEventIdxRef.current = events.length - 1;
  }, [events, sessionId, sessionNotFound, refresh]);

  // Reconnect → immediate refresh
  const prevConnectedRef = useRef(false);
  useEffect(() => {
    const wasConnected = prevConnectedRef.current;
    prevConnectedRef.current = isConnected;
    
    if (!sessionId || sessionNotFound) return;
    
    // Just connected (was disconnected)
    if (isConnected && !wasConnected) {
      refresh(sessionId);
    }
  }, [isConnected, sessionId, sessionNotFound, refresh]);

  return { 
    snapshot, 
    auctionState, 
    round, 
    setRound,  // For optimistic UI (bid response)
    isConnected, 
    lastEvent, 
    error, 
    sessionNotFound 
  };
}
