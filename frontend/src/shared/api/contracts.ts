export type SessionStatus =
  | "WAITING"
  | "COUNTDOWN"
  | "FORMATION_SELECT"
  | "AUCTIONING"
  | "MATCH_START_PENDING"
  | "FINALIZING"
  | "AWAITING_RESULT"
  | "FINISHED";

export const SESSION_STATUS_LABELS: Record<SessionStatus, string> = {
  WAITING: "Waiting for opponent",
  COUNTDOWN: "Match starting",
  FORMATION_SELECT: "Choose formation",
  AUCTIONING: "Auction in progress",
  MATCH_START_PENDING: "Confirm match start",
  FINALIZING: "Simulating match",
  AWAITING_RESULT: "Simulating match",
  FINISHED: "Match finished",
};

export const ROUND_STATUS_LABELS: Record<string, string> = {
  OPEN: "Bidding open",
  SOLD: "Sold",
  PASSED: "Passed",
};

export type Formation =
  | "FOUR_FOUR_TWO"
  | "FOUR_THREE_THREE"
  | "THREE_FIVE_TWO"
  | "FOUR_TWO_THREE_ONE"
  | "THREE_FOUR_THREE"
  | "FIVE_THREE_TWO"
  | "FOUR_ONE_FOUR_ONE"
  | "FOUR_FIVE_ONE"
  | "FOUR_THREE_TWO_ONE"
  | "FOUR_FOUR_ONE_ONE";

export type Position = "GK" | "DEF" | "CDM" | "CM" | "CAM" | "FWD";

export interface SessionSnapshotResponse {
  sessionId: string;
  status: SessionStatus;
  countdownEndsAt: string | null;
  playerCount: number;
  joined: boolean;
  myFormation: Formation | null;
  matchStartDeadlineAt: string | null;
  matchStartReadyCount: number;
  matchStartConfirmedByMe: boolean;
}

export interface AuctionRoundResponse {
  roundId: string;
  sessionId: string;
  cardId: string;
  cardName: string;
  position: string;
  cardRating: number;
  highestBidCents: number;
  highestBidderId: string | null;
  highestBidderUserId: string | null;
  currentTurnUserId: string | null;
  readyUserOneId: string | null;
  readyUserTwoId: string | null;
  endsAt: string;
  status: string;
  opponentCanBid: boolean;
}

export interface AuctionTeamCardResponse {
  cardId: string;
  cardName: string;
  position: string;
  rating: number;
  amountCents: number;
}

export interface AuctionStateResponse {
  currentRound: AuctionRoundResponse | null;
  myTeam: AuctionTeamCardResponse[];
  opponentTeam: AuctionTeamCardResponse[];
  myFormation: Formation | null;
  opponentFormation: Formation | null;
}

export interface MatchResultResponse {
  homeScore: number;
  awayScore: number;
  myTeam: AuctionTeamCardResponse[];
  opponentTeam: AuctionTeamCardResponse[];
  myFormation: Formation | null;
  opponentFormation: Formation | null;
}
