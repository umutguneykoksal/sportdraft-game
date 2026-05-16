import { Navigate, Route, Routes } from "react-router-dom";
import { LobbyPage } from "./features/lobby/LobbyPage";
import { AuctionPage } from "./features/auction/AuctionPage";
import { MatchPage } from "./features/match/MatchPage";
import { SpectatePage } from "./features/spectate/SpectatePage";

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
      <Route path="/spectate/:sessionId" element={<SpectatePage />} />
    </Routes>
  );
}
