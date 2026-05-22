import type { AuctionRoundResponse } from "../../shared/api/contracts";
import type { RoundEndedPayload } from "../../shared/realtime/eventDispatch";

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

type PlayerNick = { userId: string; nickname: string };

export function resolveWinnerLabel(
  winnerUserId: string | null | undefined,
  myUserId: string | null,
  myNickname: string,
  players?: PlayerNick[]
): string {
  if (!winnerUserId) return "Unknown";
  if (myUserId && winnerUserId === myUserId) {
    return myNickname.trim() || "You";
  }
  const other = players?.find((p) => p.userId === winnerUserId);
  return other?.nickname?.trim() || "Opponent";
}

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
  myNickname: string,
  players?: PlayerNick[]
): RoundOutcomeNotice | null {
  const round = payload.round;
  if (!round?.roundId || !round.cardName) {
    return null;
  }

  if (payload.result === "SOLD") {
    const winnerId = payload.winnerUserId ?? round.highestBidderUserId;
    const isMine = !!myUserId && winnerId === myUserId;
    const winnerLabel = resolveWinnerLabel(winnerId, myUserId, myNickname, players);
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
      : "no one bought this card";
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
