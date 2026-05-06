export type SessionStatus =
  | "WAITING"
  | "COUNTDOWN"
  | "FORMATION_SELECT"
  | "AUCTIONING"
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
  endsAt: string;
  status: string;
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
  const response = await fetch(`${API_BASE}/api/sessions/${sessionId}`, {
    method: "GET",
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(await readErrorMessage(response, `getSessionSnapshot failed: ${response.status}`));
  }
  return (await response.json()) as SessionSnapshotResponse;
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
