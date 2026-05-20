import { GameMode, LobbyRoomResponse, MatchEvent, SessionSnapshotResponse, SpectateStateResponse } from "./contracts";
import { authHeaders, getApiBase, getApiPrefix, parseOrThrow } from "./httpClient";

export async function listLobbies(): Promise<LobbyRoomResponse[]> {
  const response = await fetch(`${getApiBase()}${getApiPrefix()}/lobbies`, {
    headers: authHeaders(),
  });
  return parseOrThrow<LobbyRoomResponse[]>(response, `listLobbies failed: ${response.status}`);
}

export async function createRoom(
  nickname: string,
  gameMode: GameMode = "DUEL"
): Promise<SessionSnapshotResponse> {
  const response = await fetch(`${getApiBase()}${getApiPrefix()}/lobbies`, {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({ nickname, gameMode }),
  });
  return parseOrThrow<SessionSnapshotResponse>(response, `createRoom failed: ${response.status}`);
}

export async function joinRoom(sessionId: string, nickname: string): Promise<SessionSnapshotResponse> {
  const response = await fetch(`${getApiBase()}${getApiPrefix()}/lobbies/${sessionId}/join`, {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({ nickname }),
  });
  return parseOrThrow<SessionSnapshotResponse>(response, `joinRoom failed: ${response.status}`);
}

/** @deprecated Use createRoom + joinRoom instead. */
export async function joinLobby(nickname: string): Promise<SessionSnapshotResponse> {
  const response = await fetch(`${getApiBase()}${getApiPrefix()}/lobbies/join`, {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({ nickname }),
  });
  return parseOrThrow<SessionSnapshotResponse>(response, `joinLobby failed: ${response.status}`);
}

export async function leaveLobby(sessionId: string): Promise<SessionSnapshotResponse> {
  const response = await fetch(`${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/players/me`, {
    method: "DELETE",
    headers: authHeaders(),
  });
  return parseOrThrow<SessionSnapshotResponse>(response, `leaveLobby failed: ${response.status}`);
}

export async function getSpectateState(sessionId: string): Promise<SpectateStateResponse> {
  const response = await fetch(`${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/spectate`, {
    headers: authHeaders(),
  });
  return parseOrThrow<SpectateStateResponse>(response, `getSpectateState failed: ${response.status}`);
}

export async function getSpectateMatchTimeline(sessionId: string): Promise<MatchEvent[]> {
  const response = await fetch(`${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/spectate/match-timeline`, {
    method: "GET",
  });
  if (response.status === 404) {
    return [];
  }
  return parseOrThrow<MatchEvent[]>(response, `getSpectateMatchTimeline failed: ${response.status}`);
}
