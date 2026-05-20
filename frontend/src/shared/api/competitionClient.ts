import { CompetitionResponse } from "./contracts";
import { authHeaders, getApiBase, getApiPrefix, parseOrThrow } from "./httpClient";

export async function getCompetitionBySession(sessionId: string): Promise<CompetitionResponse> {
  const response = await fetch(
    `${getApiBase()}${getApiPrefix()}/sessions/${sessionId}/competition`,
    { headers: authHeaders() }
  );
  return parseOrThrow<CompetitionResponse>(response, `getCompetition failed: ${response.status}`);
}
