import { CompetitionMatchPage } from "../competition/CompetitionMatchPage";

export function TournamentMatchPage() {
  return (
    <CompetitionMatchPage
      config={{
        hubPath: (sessionId) => `/tournament/${sessionId}`,
        fallbackPath: "/lobby",
        backLabel: "Back to bracket",
      }}
    />
  );
}
