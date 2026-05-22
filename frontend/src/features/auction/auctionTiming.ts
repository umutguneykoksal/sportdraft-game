/** Mirrors backend AuctionTiming bid/reveal split (32s server, 30s bid UI). */
export const ROUND_REVEAL_SECONDS = 2;
export const ROUND_BID_SECONDS = 30;
export const ROUND_TOTAL_SECONDS = ROUND_REVEAL_SECONDS + ROUND_BID_SECONDS;

export function resolveRoundStartsAt(
  startsAt: string | null | undefined,
  endsAt: string
): string | null {
  if (startsAt) return startsAt;
  const endsMs = new Date(endsAt).getTime();
  if (Number.isNaN(endsMs)) return null;
  return new Date(endsMs - ROUND_TOTAL_SECONDS * 1000).toISOString();
}

export function computeServerSecondsLeft(endsAt: string, serverClockOffsetMs = 0): number {
  const adjustedNow = Date.now() + serverClockOffsetMs;
  return Math.max(0, Math.ceil((new Date(endsAt).getTime() - adjustedNow) / 1000));
}

export function isInCardRevealPhase(
  startsAt: string | null | undefined,
  endsAt: string,
  serverClockOffsetMs = 0
): boolean {
  const effectiveStartsAt = resolveRoundStartsAt(startsAt, endsAt);
  if (!effectiveStartsAt) return false;
  const adjustedNow = Date.now() + serverClockOffsetMs;
  return adjustedNow < new Date(effectiveStartsAt).getTime() + ROUND_REVEAL_SECONDS * 1000;
}

/** Player-facing countdown: 30 during reveal, then tracks remaining bid time. */
export function computeDisplaySecondsLeft(
  startsAt: string | null | undefined,
  endsAt: string,
  serverClockOffsetMs = 0
): number {
  if (isInCardRevealPhase(startsAt, endsAt, serverClockOffsetMs)) {
    return ROUND_BID_SECONDS;
  }
  return computeServerSecondsLeft(endsAt, serverClockOffsetMs);
}
