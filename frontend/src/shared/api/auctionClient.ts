import { AuctionRoundResponse, AuctionStateResponse } from "./contracts";
import { authHeaders, getApiBase, getApiPrefix, parseOrThrow } from "./httpClient";

export async function startRound(sessionId: string): Promise<AuctionRoundResponse> {
  const response = await fetch(`${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/auction/rounds/start`, {
    method: "POST",
    headers: authHeaders(),
  });
  return parseOrThrow<AuctionRoundResponse>(response, `startRound failed: ${response.status}`);
}

export async function placeBid(
  sessionId: string,
  roundId: string,
  amountCents: number
): Promise<AuctionRoundResponse> {
  const response = await fetch(`${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/bids`, {
    method: "POST",
    headers: {
      ...authHeaders(),
      "Idempotency-Key": crypto.randomUUID(),
    },
    body: JSON.stringify({ roundId, amountCents }),
  });
  return parseOrThrow<AuctionRoundResponse>(response, `placeBid failed: ${response.status}`);
}

export async function readyNextRound(sessionId: string, roundId: string): Promise<AuctionRoundResponse> {
  const response = await fetch(`${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/auction/rounds/${roundId}/ready-next`, {
    method: "POST",
    headers: {
      ...authHeaders(),
      "Idempotency-Key": crypto.randomUUID(),
    },
  });
  return parseOrThrow<AuctionRoundResponse>(response, `readyNextRound failed: ${response.status}`);
}

export async function passRound(sessionId: string, roundId: string): Promise<AuctionRoundResponse> {
  const response = await fetch(`${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/auction/rounds/${roundId}/pass`, {
    method: "POST",
    headers: {
      ...authHeaders(),
      "Idempotency-Key": crypto.randomUUID(),
    },
  });
  return parseOrThrow<AuctionRoundResponse>(response, `passRound failed: ${response.status}`);
}

export async function getAuctionState(sessionId: string): Promise<AuctionStateResponse> {
  const response = await fetch(`${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/auction/state`, {
    method: "GET",
    headers: authHeaders(),
  });
  return parseOrThrow<AuctionStateResponse>(response, `getAuctionState failed: ${response.status}`);
}

