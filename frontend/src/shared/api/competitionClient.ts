import { CompetitionMatchOpenResponse, CompetitionResponse } from "./contracts";
import { authHeaders, getApiBase, getApiPrefix, parseOrThrow } from "./httpClient";

export async function getCompetitionBySession(sessionId: string): Promise<CompetitionResponse> {
  const response = await fetch(
    `${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/competition`,
    { headers: authHeaders() }
  );
  return parseOrThrow<CompetitionResponse>(response, `getCompetition failed: ${response.status}`);
}

export async function openCompetitionMatch(
  sessionId: string,
  matchId: string,
  replay = false
): Promise<CompetitionMatchOpenResponse> {
  const replayParam = replay ? "?replay=true" : "";
  const response = await fetch(
    `${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/competition/matches/${matchId}/open${replayParam}`,
    { method: "POST", headers: authHeaders() }
  );
  return parseOrThrow<CompetitionMatchOpenResponse>(
    response,
    `openCompetitionMatch failed: ${response.status}`
  );
}

export async function ackCompetitionMatchWatch(
  sessionId: string,
  matchId: string
): Promise<CompetitionResponse> {
  const response = await fetch(
    `${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/competition/matches/${matchId}/ack-watch`,
    { method: "POST", headers: authHeaders() }
  );
  return parseOrThrow<CompetitionResponse>(response, `ackCompetitionMatchWatch failed: ${response.status}`);
}

/** @deprecated Use openCompetitionMatch */
export const openTournamentMatch = openCompetitionMatch;

/** @deprecated Use ackCompetitionMatchWatch */
export const ackTournamentMatchWatch = ackCompetitionMatchWatch;
