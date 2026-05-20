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

export const POOL_MIN_RATING_OPTIONS = [70, 75, 80] as const;
export type PoolMinRating = (typeof POOL_MIN_RATING_OPTIONS)[number];

export type PoolLeagueId = "PREMIER_LEAGUE" | "LA_LIGA" | "SERIE_A" | "BUNDESLIGA";

export const POOL_LEAGUE_OPTIONS: ReadonlyArray<{
  id: PoolLeagueId;
  label: string;
  enabled: boolean;
}> = [
  { id: "PREMIER_LEAGUE", label: "Premier League", enabled: true },
  { id: "LA_LIGA", label: "La Liga", enabled: false },
  { id: "SERIE_A", label: "Serie A", enabled: false },
  { id: "BUNDESLIGA", label: "Bundesliga", enabled: false },
];

export function poolLeagueLabel(id?: string | null): string {
  if (!id) return POOL_LEAGUE_OPTIONS[0].label;
  return POOL_LEAGUE_OPTIONS.find((o) => o.id === id)?.label ?? id;
}

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
  minPoolRating?: number;
  poolLeague?: PoolLeagueId | string;
  players?: LobbyPlayerView[];
}

export interface AuctionRoundResponse {
  roundId: string;
  sessionId: string;
  /** Session version at response time; actor clients use this to ignore stale WS patches. */
  version?: number;
  cardId: string;
  cardName: string;
  cardClubName?: string | null;
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
  clubName?: string | null;
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
  sessionHomePenaltyScore: number;
  sessionAwayPenaltyScore: number;
  myPenaltyScore: number;
  opponentPenaltyScore: number;
  winnerUserId: string;
}

export interface LobbyRoomResponse {
  sessionId: string;
  status: string;
  playerCount: number;
  maxPlayers?: number;
  gameMode?: GameMode;
  minPoolRating?: number;
  poolLeague?: PoolLeagueId | string;
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
  homePenaltyScore: number | null;
  awayPenaltyScore: number | null;
  status: string;
  playable: boolean;
  watchedByMe: boolean;
  allPlayersWatched: boolean;
  watchCount: number;
  requiredWatchers: number;
}

export interface CompetitionMatchOpenResponse {
  matchId: string;
  roundKey: string;
  homeUserId: string;
  homeNickname: string;
  awayUserId: string;
  awayNickname: string;
  homeFormation: string;
  awayFormation: string;
  timeline: MatchEvent[];
  homeScore: number | null;
  awayScore: number | null;
  homePenaltyScore: number | null;
  awayPenaltyScore: number | null;
  watchedByMe: boolean;
  allPlayersWatched: boolean;
  watchCount: number;
  requiredWatchers: number;
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
  | "FULL_TIME"
  | "PENALTIES_START"
  | "PENALTY_SCORED"
  | "PENALTY_MISSED"
  | "PENALTIES_END";

export interface MatchEvent {
  minute: number;
  type: MatchEventType;
  teamId: string | null;
  playerId: string | null;
  playerName: string | null;
  detail: string;
}
