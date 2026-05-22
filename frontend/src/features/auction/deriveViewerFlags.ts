import type { AuctionRoundResponse, AuctionTeamCardResponse, Formation } from "../../shared/api/contracts";
import { openEligibleHighlights } from "../../shared/positions";
import { isInCardRevealPhase } from "./auctionTiming";

export interface ViewerFlags {
  viewerCanBid: boolean;
  viewerCanTake: boolean;
  viewerCanPass: boolean;
  viewerCanFillCard: boolean;
}

/**
 * Pure function that mirrors AuctionService.computeViewer* logic on the frontend.
 *
 * Called after every SSE patch so viewer flags stay accurate without a REST call.
 * The server remains authoritative — it will reject invalid actions regardless.
 */
export function deriveViewerFlags(
  round: AuctionRoundResponse | null,
  myUserId: string | null,
  myTeam: AuctionTeamCardResponse[],
  myFormation: Formation,
  budget: number,
  serverClockOffsetMs: number,
  /** When set (from timed hook), keeps canBid in sync with UI turn-open phase. */
  turnIsOpenKnown?: boolean
): ViewerFlags {
  const none: ViewerFlags = {
    viewerCanBid: false,
    viewerCanTake: false,
    viewerCanPass: false,
    viewerCanFillCard: false,
  };

  if (!round || round.status !== "OPEN" || !myUserId) return none;

  const adjustedNow = Date.now() + serverClockOffsetMs;
  if (isInCardRevealPhase(round.startsAt, round.endsAt, serverClockOffsetMs)) return none;

  const minimumBid = Math.max(1, round.highestBidCents + 1);
  const rosterFull = myTeam.length >= 11;

  const eligiblePositions =
    round.eligiblePositions?.length ? round.eligiblePositions : [round.position];
  const canFillCard =
    !rosterFull && openEligibleHighlights(myFormation, myTeam, eligiblePositions) != null;

  const hasPassed = (round.passedPlayerIds ?? []).includes(myUserId);
  const isMyTurn = round.currentTurnUserId === myUserId;

  const turnIsOpen =
    turnIsOpenKnown ??
    (round.turnOpenAt != null && adjustedNow >= new Date(round.turnOpenAt).getTime());

  const mayAct = (isMyTurn || turnIsOpen) && !hasPassed;
  const hasBudget = budget >= minimumBid;
  const isLeadingBidder =
    round.highestBidCents > 0 && round.highestBidderUserId === myUserId;

  const viewerCanFillCard = canFillCard;
  const viewerCanBid = mayAct && canFillCard && hasBudget && !isLeadingBidder;
  const viewerCanPass = mayAct && !isLeadingBidder;
  // Take = my exclusive turn + opponent cannot bid on this card + I can fill + I have budget
  const viewerCanTake =
    isMyTurn && !turnIsOpen && !hasPassed && round.opponentCanBid === false && canFillCard && hasBudget;

  return { viewerCanBid, viewerCanTake, viewerCanPass, viewerCanFillCard };
}
