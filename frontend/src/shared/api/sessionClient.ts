import { Formation, SessionSnapshotResponse } from "./contracts";
import { authHeaders, fetchWithTimeout, getApiBase, parseOrThrow } from "./httpClient";

export async function getSessionSnapshot(sessionId: string): Promise<SessionSnapshotResponse> {
  let lastError: Error | null = null;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      const response = await fetchWithTimeout(
        `${getApiBase()}/api/sessions/${sessionId}`,
        { method: "GET", headers: authHeaders() },
        3500
      );
      return await parseOrThrow<SessionSnapshotResponse>(response, `getSessionSnapshot failed: ${response.status}`);
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
  const response = await fetch(`${getApiBase()}/api/sessions/${sessionId}/formation`, {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify({ formation }),
  });
  return parseOrThrow<SessionSnapshotResponse>(response, `selectFormation failed: ${response.status}`);
}

export async function confirmFormationReady(sessionId: string): Promise<SessionSnapshotResponse> {
  const response = await fetch(`${getApiBase()}/api/sessions/${sessionId}/confirm-formation-ready`, {
    method: "POST",
    headers: authHeaders(),
  });
  return parseOrThrow<SessionSnapshotResponse>(response, `confirmFormationReady failed: ${response.status}`);
}

export async function confirmMatchStart(sessionId: string): Promise<SessionSnapshotResponse> {
  const response = await fetch(`${getApiBase()}/api/sessions/${sessionId}/confirm-match-start`, {
    method: "POST",
    headers: authHeaders(),
  });
  return parseOrThrow<SessionSnapshotResponse>(response, `confirmMatchStart failed: ${response.status}`);
}

export async function pingSession(sessionId: string): Promise<void> {
  try {
    await fetch(`${getApiBase()}/api/sessions/${sessionId}/ping`, {
      method: "POST",
      headers: authHeaders(),
    });
  } catch {
    // Fire-and-forget: swallow errors silently
  }
}

export interface EventSyncResponse {
  events: unknown[];
  lastSeq: number;
}

/** Fetches events missed since afterSeq. Returns empty events if gap is beyond ring buffer. */
export async function fetchEventsSince(
  sessionId: string,
  afterSeq: number,
  spectator = false
): Promise<EventSyncResponse> {
  const token = localStorage.getItem("sessionToken") ?? "";
  const spectatorParam = spectator ? "&spectator=true" : "";
  const response = await fetch(
    `${getApiBase()}/api/sessions/${sessionId}/events/sync?afterSeq=${afterSeq}&token=${encodeURIComponent(token)}${spectatorParam}`,
    { method: "GET" }
  );
  if (!response.ok) throw new Error(`fetchEventsSince failed: ${response.status}`);
  return response.json() as Promise<EventSyncResponse>;
}
