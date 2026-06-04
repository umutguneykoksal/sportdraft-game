import type { AuctionTeamCardResponse } from "../api/contracts";

export const POSITION_COLORS: Record<string, string> = {
  GK: "bg-yellow-500",
  DEF: "bg-blue-500",
  CDM: "bg-indigo-500",
  CM: "bg-green-500",
  CAM: "bg-orange-500",
  FWD: "bg-red-500",
};

export function mapAuctionTeamCards(
  raw: AuctionTeamCardResponse[]
): AuctionTeamCardResponse[] {
  return raw.map((c) => ({
    cardId: c.cardId,
    cardName: c.cardName,
    clubName: c.clubName,
    position: c.position,
    rating: c.rating ?? 0,
    amountCents: c.amountCents,
  }));
}
