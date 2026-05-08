import { AuctionRoundResponse } from "../../../shared/api";

export function deriveAuctionViewModel(round: AuctionRoundResponse | null, myUserId: string | null) {
  const isMyTurn = !!myUserId && !!round && round.currentTurnUserId === myUserId;
  const isReadyByMe = !!myUserId && !!round && (round.readyUserOneId === myUserId || round.readyUserTwoId === myUserId);
  const readyCount = round ? [round.readyUserOneId, round.readyUserTwoId].filter(Boolean).length : 0;
  return { isMyTurn, isReadyByMe, readyCount };
}
