import { useCallback, useEffect, useRef, useState } from "react";
import type { MatchEvent } from "../../../shared/api/contracts";
import { getSpectateMatchTimeline } from "../../../shared/api/lobbyClient";
import { MATCH_REPLAY_POLL_MS, SIMULATION_STALL_WARNING_MS } from "../../../shared/realtime/syncConstants";

export type SpectateMatchPhase = "auction" | "simulating" | "loading_replay" | "commentary" | "result";

const POLL_MS = MATCH_REPLAY_POLL_MS;

export function useSpectateMatchReplay(
  sessionId: string | null,
  status: string | undefined,
  matchCompleted: boolean
) {
  const [phase, setPhase] = useState<SpectateMatchPhase>("auction");
  const [timeline, setTimeline] = useState<MatchEvent[]>([]);
  const [simulationStalled, setSimulationStalled] = useState(false);
  const loadedRef = useRef(false);
  const waitingSinceRef = useRef<number | null>(null);

  const isSimulating = status === "AWAITING_RESULT" || status === "FINALIZING";
  const matchEnded =
    matchCompleted || status === "FINISHED" || status === "AWAITING_RESULT" || status === "FINALIZING";
  const simulationSettled = status === "FINISHED" || matchCompleted;

  useEffect(() => {
    loadedRef.current = false;
    waitingSinceRef.current = null;
    setSimulationStalled(false);
    setTimeline([]);
    setPhase("auction");
  }, [sessionId]);

  useEffect(() => {
    if (!matchEnded) return;
    if (loadedRef.current) return;
    setPhase(isSimulating ? "simulating" : "loading_replay");
  }, [matchEnded, isSimulating]);

  useEffect(() => {
    if (!matchEnded || loadedRef.current) {
      waitingSinceRef.current = null;
      setSimulationStalled(false);
      return;
    }
    if (waitingSinceRef.current === null) {
      waitingSinceRef.current = Date.now();
    }
    const id = window.setInterval(() => {
      const since = waitingSinceRef.current;
      if (since != null && Date.now() - since >= SIMULATION_STALL_WARNING_MS) {
        setSimulationStalled(true);
      }
    }, 5_000);
    return () => window.clearInterval(id);
  }, [matchEnded, isSimulating, simulationSettled]);

  const fetchTimeline = useCallback(async (): Promise<boolean> => {
    if (!sessionId) return false;
    try {
      const data = await getSpectateMatchTimeline(sessionId);
      if (data.length > 0) {
        setTimeline(data);
        loadedRef.current = true;
        waitingSinceRef.current = null;
        setSimulationStalled(false);
        setPhase((current) => (current === "result" ? "result" : "commentary"));
        return true;
      }
    } catch {
      // retry while server is still simulating
    }
    return false;
  }, [sessionId]);

  useEffect(() => {
    if (!sessionId || !matchEnded || loadedRef.current) return;

    let cancelled = false;
    let intervalId: number | null = null;

    const stop = () => {
      if (intervalId != null) {
        window.clearInterval(intervalId);
        intervalId = null;
      }
    };

    const poll = async () => {
      if (cancelled || loadedRef.current) {
        stop();
        return;
      }

      if (!isSimulating) {
        setPhase((current) => (current === "simulating" ? "loading_replay" : current));
      }

      const ready = await fetchTimeline();
      if (cancelled || loadedRef.current) {
        stop();
        return;
      }

      if (ready) {
        stop();
        return;
      }

      // Server finished but timeline missing (edge case) — show score-only result.
      if (simulationSettled) {
        waitingSinceRef.current = null;
        setSimulationStalled(false);
        setPhase((current) => (current === "commentary" ? "commentary" : "result"));
        stop();
      }
    };

    poll();
    intervalId = window.setInterval(poll, POLL_MS);

    return () => {
      cancelled = true;
      stop();
    };
  }, [sessionId, matchEnded, isSimulating, simulationSettled, fetchTimeline]);

  const showFinalResult = useCallback(() => {
    setPhase("result");
  }, []);

  return { phase, timeline, simulationStalled, showFinalResult };
}
