import { MatchResultResponse, MatchEvent } from "./contracts";
import { authHeaders, getApiBase } from "./httpClient";
import { ApiError, readErrorMessage } from "./errors";

export async function getMatchResult(sessionId: string): Promise<MatchResultResponse | null> {
  const response = await fetch(`${getApiBase()}/api/sessions/${sessionId}/match-result`, {
    method: "GET",
    headers: authHeaders(),
  });
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response, `getMatchResult failed: ${response.status}`), response.status);
  }
  return (await response.json()) as MatchResultResponse;
}

export async function getMatchTimeline(sessionId: string): Promise<MatchEvent[]> {
  const response = await fetch(`${getApiBase()}/api/sessions/${sessionId}/match-timeline`, {
    method: "GET",
    headers: authHeaders(),
  });
  if (response.status === 404) {
    return [];
  }
  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response, `getMatchTimeline failed: ${response.status}`), response.status);
  }
  return (await response.json()) as MatchEvent[];
}
