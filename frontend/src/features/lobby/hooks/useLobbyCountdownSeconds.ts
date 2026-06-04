import { useEffect, useMemo, useRef, useState } from "react";
import type { SessionSnapshotResponse } from "../../../shared/api/contracts";
import {
  computeCountdownSecondsLeft,
  countdownLatchKey,
  inferCountdownPhase,
  latchStaleCountdownDeadline,
  maxCountdownSecondsForPhase,
  resolveLobbyCountdownDeadline,
} from "../../../shared/lobbyTiming";

/** Derives countdown from snapshot each tick — no stale seconds when phase changes. */
export function useLobbyCountdownSeconds(
  snapshot: SessionSnapshotResponse | null
): number | null {
  const [tick, setTick] = useState(0);
  const latchedDeadlineRef = useRef<{ key: string; deadline: string } | null>(null);

  const rawDeadlineAt = resolveLobbyCountdownDeadline(snapshot);
  const latchKey = countdownLatchKey(snapshot);
  const phase = inferCountdownPhase(snapshot);
  const maxSeconds = maxCountdownSecondsForPhase(phase);

  const deadlineAt = useMemo(() => {
    if (!rawDeadlineAt || !latchKey || maxSeconds == null) {
      latchedDeadlineRef.current = null;
      return rawDeadlineAt;
    }

    const rawSeconds = computeCountdownSecondsLeft(rawDeadlineAt);
    if (rawSeconds == null || rawSeconds <= maxSeconds + 1) {
      latchedDeadlineRef.current = null;
      return rawDeadlineAt;
    }

    if (latchedDeadlineRef.current?.key !== latchKey) {
      latchedDeadlineRef.current = {
        key: latchKey,
        deadline: latchStaleCountdownDeadline(rawDeadlineAt, maxSeconds, Date.now()),
      };
    }
    return latchedDeadlineRef.current.deadline;
  }, [rawDeadlineAt, latchKey, maxSeconds]);

  useEffect(() => {
    if (!deadlineAt) return;
    const timer = setInterval(() => setTick((n) => n + 1), 250);
    return () => clearInterval(timer);
  }, [deadlineAt, snapshot?.status, snapshot?.countdownPhase]);

  return useMemo(
    () => computeCountdownSecondsLeft(deadlineAt),
    [deadlineAt, tick]
  );
}
