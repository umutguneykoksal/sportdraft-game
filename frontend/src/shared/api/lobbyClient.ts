import { SessionSnapshotResponse } from "./contracts";
import { authHeaders, getApiBase, parseOrThrow } from "./httpClient";

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
