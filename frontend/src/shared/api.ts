export type SessionStatus =
  | "WAITING"
  | "COUNTDOWN"
  | "FORMATION_SELECT"
  | "AUCTIONING"
  | "MATCH_START_PENDING"
  | "FINALIZING"
  | "AWAITING_RESULT"
  | "FINISHED";

export type Formation = "FOUR_FOUR_TWO" | "FOUR_THREE_THREE" | "THREE_FIVE_TWO";

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
  highestBidCents: number;
  highestBidderId: string | null;
  currentTurnUserId: string | null;
  readyUserOneId: string | null;
  readyUserTwoId: string | null;
  endsAt: string;
  status: string;
}

export interface AuctionTeamCardResponse {
  cardId: string;
  cardName: string;
  position: string;
  amountCents: number;
}

export interface AuctionStateResponse {
  currentRound: AuctionRoundResponse | null;
  myTeam: AuctionTeamCardResponse[];
  opponentTeam: AuctionTeamCardResponse[];
}

const API_BASE = process.env.REACT_APP_API_BASE ?? "http://localhost:8080";

export function getApiBase(): string {
  return API_BASE;
}

function authHeaders(): Record<string, string> {
  const token = localStorage.getItem("sessionToken");
  if (!token) {
    throw new Error("sessionToken not found in localStorage");
  }
  return {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };
}

async function readErrorMessage(response: Response, fallback: string): Promise<string> {
  const text = await response.text();
  if (!text) {
    return fallback;
  }
  try {
    const parsed = JSON.parse(text) as { detail?: string; message?: string; error?: string };
    return parsed.detail ?? parsed.message ?? parsed.error ?? fallback;
  } catch {
    return text;
  }
}

async function fetchWithTimeout(url: string, init: RequestInit, timeoutMs: number): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    return await fetch(url, { ...init, signal: controller.signal });
  } finally {
    clearTimeout(timer);
  }
}

export async function joinLobby(nickname: string): Promise<SessionSnapshotResponse> {
  const response = await fetch(`${API_BASE}/api/lobbies/join`, {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({ nickname }),
  });
  if (!response.ok) {
    throw new Error(await readErrorMessage(response, `joinLobby failed: ${response.status}`));
  }
  return (await response.json()) as SessionSnapshotResponse;
}

export async function leaveLobby(sessionId: string): Promise<SessionSnapshotResponse> {
  const response = await fetch(`${API_BASE}/api/sessions/${sessionId}/players/me`, {
    method: "DELETE",
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(await readErrorMessage(response, `leaveLobby failed: ${response.status}`));
  }
  return (await response.json()) as SessionSnapshotResponse;
}

export async function getSessionSnapshot(sessionId: string): Promise<SessionSnapshotResponse> {
  let lastError: Error | null = null;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const response = await fetchWithTimeout(
        `${API_BASE}/api/sessions/${sessionId}`,
        {
          method: "GET",
          headers: authHeaders(),
        },
        3500
      );
      if (!response.ok) {
        throw new Error(await readErrorMessage(response, `getSessionSnapshot failed: ${response.status}`));
      }
      return (await response.json()) as SessionSnapshotResponse;
    } catch (error) {
      lastError = error instanceof Error ? error : new Error("getSessionSnapshot failed");
      if (attempt === 0) {
        await new Promise((resolve) => setTimeout(resolve, 250));
      }
    }
  }
  throw lastError ?? new Error("getSessionSnapshot failed");
}

export async function selectFormation(sessionId: string, formation: Formation): Promise<SessionSnapshotResponse> {
  const response = await fetch(`${API_BASE}/api/sessions/${sessionId}/formation`, {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({ formation }),
  });
  if (!response.ok) {
    throw new Error(await readErrorMessage(response, `selectFormation failed: ${response.status}`));
  }
  return (await response.json()) as SessionSnapshotResponse;
}

export async function confirmMatchStart(sessionId: string): Promise<SessionSnapshotResponse> {
  const response = await fetch(`${API_BASE}/api/sessions/${sessionId}/confirm-match-start`, {
    method: "POST",
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(await readErrorMessage(response, `confirmMatchStart failed: ${response.status}`));
  }
  return (await response.json()) as SessionSnapshotResponse;
}

export async function startRound(sessionId: string): Promise<AuctionRoundResponse> {
  const response = await fetch(`${API_BASE}/api/sessions/${sessionId}/auction/rounds/start`, {
    method: "POST",
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(await readErrorMessage(response, `startRound failed: ${response.status}`));
  }
  return (await response.json()) as AuctionRoundResponse;
}

export async function placeBid(
  sessionId: string,
  roundId: string,
  amountCents: number
): Promise<AuctionRoundResponse> {
  const response = await fetch(`${API_BASE}/api/sessions/${sessionId}/bids`, {
    method: "POST",
    headers: {
      ...authHeaders(),
      "Idempotency-Key": crypto.randomUUID(),
    },
    body: JSON.stringify({ roundId, amountCents }),
  });
  if (!response.ok) {
    throw new Error(await readErrorMessage(response, `placeBid failed: ${response.status}`));
  }
  return (await response.json()) as AuctionRoundResponse;
}

export async function readyNextRound(sessionId: string, roundId: string): Promise<AuctionRoundResponse> {
  const response = await fetch(`${API_BASE}/api/sessions/${sessionId}/auction/rounds/${roundId}/ready-next`, {
    method: "POST",
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(await readErrorMessage(response, `readyNextRound failed: ${response.status}`));
  }
  return (await response.json()) as AuctionRoundResponse;
}

export async function passRound(sessionId: string, roundId: string): Promise<AuctionRoundResponse> {
  const response = await fetch(`${API_BASE}/api/sessions/${sessionId}/auction/rounds/${roundId}/pass`, {
    method: "POST",
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(await readErrorMessage(response, `passRound failed: ${response.status}`));
  }
  return (await response.json()) as AuctionRoundResponse;
}

export async function getAuctionState(sessionId: string): Promise<AuctionStateResponse> {
  const response = await fetch(`${API_BASE}/api/sessions/${sessionId}/auction/state`, {
    method: "GET",
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(await readErrorMessage(response, `getAuctionState failed: ${response.status}`));
  }
  return (await response.json()) as AuctionStateResponse;
}
