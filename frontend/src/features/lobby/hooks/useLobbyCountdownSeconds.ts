import { useEffect, useMemo, useState } from "react";
import type { SessionSnapshotResponse } from "../../../shared/api/contracts";
import {
  computeCountdownSecondsLeft,
  resolveLobbyCountdownDeadline,
} from "../../../shared/lobbyTiming";

/** Derives countdown from snapshot each tick — no stale seconds when phase changes. */
export function useLobbyCountdownSeconds(
  snapshot: SessionSnapshotResponse | null
): number | null {
  const [tick, setTick] = useState(0);

  const deadlineAt = resolveLobbyCountdownDeadline(snapshot);

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
