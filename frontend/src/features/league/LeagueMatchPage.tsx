import { CompetitionMatchPage } from "../competition/CompetitionMatchPage";
import { matchdayLabel } from "./leagueFlow";

export function LeagueMatchPage() {
  return (
    <CompetitionMatchPage
      config={{
        hubPath: (sessionId) => `/league/${sessionId}`,
        fallbackPath: "/lobby",
        backLabel: "Back to league",
        formatRoundLabel: matchdayLabel,
      }}
    />
  );
}
