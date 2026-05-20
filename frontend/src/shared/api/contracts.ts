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

export type GameMode = "DUEL" | "TOURNAMENT" | "LEAGUE";

export const GAME_MODE_LABELS: Record<GameMode, string> = {
  DUEL: "Duel (2 players)",
  TOURNAMENT: "Tournament (4 players)",
  LEAGUE: "League (4 players)",
};

export interface LobbyPlayerView {
  userId: string;
  nickname: string;
  seat: number;
}

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
  version?: number;
  lastSeq?: number;
  gameMode?: GameMode;
  maxPlayers?: number;
  players?: LobbyPlayerView[];
}

export interface AuctionRoundResponse {
  roundId: string;
  sessionId: string;
  /** Session version at response time; actor clients use this to ignore stale WS patches. */
  version?: number;
  cardId: string;
  cardName: string;
  position: string;
  eligiblePositions?: string[];
  cardRating: number;
  pace?: number;
  shooting?: number;
  passing?: number;
  defending?: number;
  physical?: number;
  highestBidCents: number;
  highestBidderId: string | null;
  highestBidderUserId: string | null;
  currentTurnUserId: string | null;
  readyUserOneId: string | null;
  readyUserTwoId: string | null;
  endsAt: string;
  turnOpenAt: string | null;
  status: string;
  opponentCanBid: boolean;
  viewerCanBid: boolean;
  viewerCanTake: boolean;
  viewerCanPass: boolean;
  viewerCanFillCard: boolean;
  /** UUIDs of players who permanently passed this round — included in SSE payloads so clients can derive viewer flags without a REST call. */
  passedPlayerIds: string[];
}

export interface AuctionTeamCardResponse {
  cardId: string;
  cardName: string;
  position: string;
  rating: number;
  amountCents: number;
}

export interface PlayerAuctionView {
  userId: string;
  nickname: string;
  budgetRemaining: number;
  rosterSize: number;
  currentTurn: boolean;
  formation: Formation | string;
  team: AuctionTeamCardResponse[];
}

export interface AuctionStateResponse {
  currentRound: AuctionRoundResponse | null;
  myTeam: AuctionTeamCardResponse[];
  opponentTeam: AuctionTeamCardResponse[];
  myFormation: Formation | null;
  opponentFormation: Formation | null;
  version?: number;
  players?: PlayerAuctionView[];
}

export interface MatchResultResponse {
  homeScore: number;
  awayScore: number;
  myTeam: AuctionTeamCardResponse[];
  opponentTeam: AuctionTeamCardResponse[];
  myFormation: Formation | null;
  opponentFormation: Formation | null;
  myNickname: string | null;
  opponentNickname: string | null;
  myTeamId: string | null;
  myShots: number;
  opponentShots: number;
  myFouls: number;
  opponentFouls: number;
  homeUserId: string;
  awayUserId: string;
  sessionHomeNickname: string;
  sessionAwayNickname: string;
  sessionHomeScore: number;
  sessionAwayScore: number;
}

export interface LobbyRoomResponse {
  sessionId: string;
  status: string;
  playerCount: number;
  maxPlayers?: number;
  gameMode?: GameMode;
  playerNicknames: string[];
  createdAt: string;
  spectatorCount: number;
}

export interface CompetitionMatchResponse {
  matchId: string;
  roundKey: string;
  homeUserId: string;
  homeNickname: string;
  awayUserId: string;
  awayNickname: string;
  homeScore: number | null;
  awayScore: number | null;
  status: string;
}

export interface CompetitionResponse {
  competitionId: string;
  sessionId: string;
  type: string;
  status: string;
  championUserId: string | null;
  championNickname: string | null;
  matches: CompetitionMatchResponse[];
}

export interface SpectateStateResponse {
  status: string;
  currentRound: AuctionRoundResponse | null;
  homeTeam: AuctionTeamCardResponse[];
  awayTeam: AuctionTeamCardResponse[];
  homeFormation: string;
  awayFormation: string;
  homeUserId: string;
  awayUserId: string;
  homeNickname: string;
  awayNickname: string;
  homeBudget: number;
  awayBudget: number;
  homeScore: number;
  awayScore: number;
}

// Match Timeline for 2D replay
export type MatchEventType =
  | "KICK_OFF"
  | "SHOT_SAVED"
  | "SHOT_MISSED"
  | "GOAL"
  | "FOUL"
  | "HALF_TIME"
  | "FULL_TIME";

export interface MatchEvent {
  minute: number;
  type: MatchEventType;
  teamId: string | null;
  playerId: string | null;
  playerName: string | null;
  detail: string;
}
