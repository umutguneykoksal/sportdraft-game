import type { AuctionRoundResponse } from "../../shared/api/contracts";

export type RoundOutcomeNotice = {
  roundId: string;
  kind: "sold" | "passed";
  cardName: string;
  cardRating?: number;
  amountCents?: number;
  /** Display label: player nickname or "You" / "Opponent" */
  winnerLabel?: string;
  isMine?: boolean;
  detail?: string;
};

export type RoundEndedPayload = {
  result?: string;
  reason?: string;
  winnerUserId?: string;
  round?: AuctionRoundResponse;
};

const PASSED_REASON_LABELS: Record<string, string> = {
  both_passed: "both players passed",
  winner_cannot_take: "winner could not take the card",
  winner_roster_full: "winner's squad is full",
  winner_position_full: "no slot for winner",
  both_rosters_complete: "squads complete",
};

export function buildRoundOutcomeNotice(
  payload: RoundEndedPayload,
  myUserId: string | null,
  myNickname: string
): RoundOutcomeNotice | null {
  const round = payload.round;
  if (!round?.roundId || !round.cardName) {
    return null;
  }

  if (payload.result === "SOLD") {
    const winnerId = payload.winnerUserId ?? round.highestBidderUserId;
    const isMine = !!myUserId && winnerId === myUserId;
    const winnerLabel = isMine
      ? (myNickname.trim() || "You")
      : "Opponent";
    return {
      roundId: round.roundId,
      kind: "sold",
      cardName: round.cardName,
      cardRating: round.cardRating,
      amountCents: round.highestBidCents,
      winnerLabel,
      isMine,
    };
  }

  if (payload.result === "PASSED") {
    const reason = payload.reason
      ? PASSED_REASON_LABELS[payload.reason] ?? payload.reason.replace(/_/g, " ")
      : "no winning bid";
    return {
      roundId: round.roundId,
      kind: "passed",
      cardName: round.cardName,
      cardRating: round.cardRating,
      detail: reason,
    };
  }

  return null;
}
