import type { Formation, AuctionTeamCardResponse, Position } from "./api/contracts";
import { FORMATION_DOTS } from "./formations";

export function formatEligiblePositions(primary: string, eligiblePositions?: string[]): string {
  const positions = eligiblePositions?.length ? eligiblePositions : [primary];
  return Array.from(new Set(positions)).join(" · ");
}

/** Formation slots still open for any of the round card's eligible positions. */
export function openEligibleHighlights(
  formation: Formation,
  cards: AuctionTeamCardResponse[],
  eligiblePositions?: string[],
): Position[] | undefined {
  if (!eligiblePositions?.length) {
    return undefined;
  }
  const required: Partial<Record<Position, number>> = {};
  for (const dot of FORMATION_DOTS[formation]) {
    required[dot.pos] = (required[dot.pos] ?? 0) + 1;
  }
  const filled: Partial<Record<Position, number>> = {};
  for (const c of cards) {
    const pos = c.position as Position;
    filled[pos] = (filled[pos] ?? 0) + 1;
  }
  const open = eligiblePositions.filter((pos) => {
    const p = pos as Position;
    return (filled[p] ?? 0) < (required[p] ?? 0);
  }) as Position[];
  return open.length > 0 ? open : undefined;
}
