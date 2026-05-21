import { Navigate, Route, Routes } from "react-router-dom";
import { LobbyPage } from "./features/lobby/LobbyPage";
import { AuctionPage } from "./features/auction/AuctionPage";
import { MatchPage } from "./features/match/MatchPage";
import { SpectatePage } from "./features/spectate/SpectatePage";
import { TournamentBracketPage } from "./features/tournament/TournamentBracketPage";
import { TournamentMatchPage } from "./features/tournament/TournamentMatchPage";
import { LeagueTablePage } from "./features/league/LeagueTablePage";
import { LeagueMatchPage } from "./features/league/LeagueMatchPage";

export function App() {
  // Always land on lobby first; useLobbyFlow validates activeSessionId against the server
  // and only navigates to /auction when the session still exists in Redis.
  const defaultRoute = "/lobby";
  return (
    <Routes>
      <Route path="/" element={<Navigate to={defaultRoute} replace />} />
      <Route path="/lobby" element={<LobbyPage />} />
      <Route path="/auction/:sessionId" element={<AuctionPage />} />
      <Route path="/match/:sessionId" element={<MatchPage />} />
      <Route path="/tournament/:sessionId" element={<TournamentBracketPage />} />
      <Route path="/tournament/:sessionId/match/:matchId" element={<TournamentMatchPage />} />
      <Route path="/league/:sessionId" element={<LeagueTablePage />} />
      <Route path="/league/:sessionId/match/:matchId" element={<LeagueMatchPage />} />
      <Route path="/spectate/:sessionId" element={<SpectatePage />} />
    </Routes>
  );
}
