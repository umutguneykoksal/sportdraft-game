import type { AuctionRoundResponse, SpectateStateResponse } from "../../shared/api/contracts";

export function nicknameForUserId(
  userId: string | null | undefined,
  state: SpectateStateResponse
): string | null {
  if (!userId) return null;
  if (userId === state.homeUserId) return state.homeNickname;
  if (userId === state.awayUserId) return state.awayNickname;
  return null;
}

export function deriveSpectateTurnMessage(
  round: AuctionRoundResponse,
  state: SpectateStateResponse,
  turnIsOpen: boolean
): { message: string; tone: "open" | "active" | "waiting" | "leading" } {
  const turnNickname = nicknameForUserId(round.currentTurnUserId, state);
  const leaderNickname = nicknameForUserId(round.highestBidderUserId, state);
  const opponentHasBid = (round.highestBidCents ?? 0) > 0;

  if (turnIsOpen) {
    return { message: "Bidding open to both players!", tone: "open" };
  }

  if (round.currentTurnUserId && turnNickname) {
    if (opponentHasBid && leaderNickname && round.highestBidderUserId !== round.currentTurnUserId) {
      return {
        message: `${leaderNickname} leads at ${round.highestBidCents} cr — ${turnNickname}'s turn`,
        tone: "active",
      };
    }
    return { message: `${turnNickname}'s turn to bid`, tone: "active" };
  }

  if (opponentHasBid && leaderNickname) {
    return {
      message: `${leaderNickname} leads at ${round.highestBidCents} cr`,
      tone: "leading",
    };
  }

  return { message: "Waiting for opening bid…", tone: "waiting" };
}
