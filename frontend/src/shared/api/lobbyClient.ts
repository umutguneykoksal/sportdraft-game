import { LobbyRoomResponse, SessionSnapshotResponse, SpectateStateResponse } from "./contracts";
import { authHeaders, getApiBase, parseOrThrow } from "./httpClient";

export async function listLobbies(): Promise<LobbyRoomResponse[]> {
  const response = await fetch(`${getApiBase()}/api/lobbies`, {
    headers: authHeaders(),
  });
  return parseOrThrow<LobbyRoomResponse[]>(response, `listLobbies failed: ${response.status}`);
}

export async function createRoom(nickname: string): Promise<SessionSnapshotResponse> {
  const response = await fetch(`${getApiBase()}/api/lobbies`, {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({ nickname }),
  });
  return parseOrThrow<SessionSnapshotResponse>(response, `createRoom failed: ${response.status}`);
}

export async function joinRoom(sessionId: string, nickname: string): Promise<SessionSnapshotResponse> {
  const response = await fetch(`${getApiBase()}/api/lobbies/${sessionId}/join`, {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({ nickname }),
  });
  return parseOrThrow<SessionSnapshotResponse>(response, `joinRoom failed: ${response.status}`);
}

/** @deprecated Use createRoom + joinRoom instead. */
export async function joinLobby(nickname: string): Promise<SessionSnapshotResponse> {
  const response = await fetch(`${getApiBase()}/api/lobbies/join`, {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({ nickname }),
  });
  return parseOrThrow<SessionSnapshotResponse>(response, `joinLobby failed: ${response.status}`);
}

export async function leaveLobby(sessionId: string): Promise<SessionSnapshotResponse> {
  const response = await fetch(`${getApiBase()}/api/sessions/${sessionId}/players/me`, {
    method: "DELETE",
    headers: authHeaders(),
  });
  return parseOrThrow<SessionSnapshotResponse>(response, `leaveLobby failed: ${response.status}`);
}

export async function getSpectateState(sessionId: string): Promise<SpectateStateResponse> {
  const response = await fetch(`${getApiBase()}/api/sessions/${sessionId}/spectate`, {
    headers: authHeaders(),
  });
  return parseOrThrow<SpectateStateResponse>(response, `getSpectateState failed: ${response.status}`);
}
