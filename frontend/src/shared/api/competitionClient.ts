import { CompetitionMatchOpenResponse, CompetitionResponse } from "./contracts";
import { authHeaders, getApiBase, getApiPrefix, parseOrThrow } from "./httpClient";

export async function getCompetitionBySession(sessionId: string): Promise<CompetitionResponse> {
  const response = await fetch(
    `${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/competition`,
    { headers: authHeaders() }
  );
  return parseOrThrow<CompetitionResponse>(response, `getCompetition failed: ${response.status}`);
}

export async function openTournamentMatch(
  sessionId: string,
  matchId: string
): Promise<CompetitionMatchOpenResponse> {
  const response = await fetch(
    `${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/competition/matches/${matchId}/open`,
    { method: "POST", headers: authHeaders() }
  );
  return parseOrThrow<CompetitionMatchOpenResponse>(
    response,
    `openTournamentMatch failed: ${response.status}`
  );
}

export async function ackTournamentMatchWatch(
  sessionId: string,
  matchId: string
): Promise<CompetitionResponse> {
  const response = await fetch(
    `${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/competition/matches/${matchId}/ack-watch`,
    { method: "POST", headers: authHeaders() }
  );
  return parseOrThrow<CompetitionResponse>(response, `ackTournamentMatchWatch failed: ${response.status}`);
}
