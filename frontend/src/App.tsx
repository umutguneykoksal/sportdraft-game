import { Navigate, Route, Routes } from "react-router-dom";
import { LobbyPage } from "./features/lobby/LobbyPage";
import { AuctionPage } from "./features/auction/AuctionPage";
import { MatchPage } from "./features/match/MatchPage";
import { getActiveSessionId } from "./shared/session";

export function App() {
  const activeSessionId = getActiveSessionId();
  const defaultRoute = activeSessionId ? `/auction/${activeSessionId}` : "/lobby";
  return (
    <Routes>
      <Route path="/" element={<Navigate to={defaultRoute} replace />} />
      <Route path="/lobby" element={<LobbyPage />} />
      <Route path="/auction/:sessionId" element={<AuctionPage />} />
      <Route path="/match/:sessionId" element={<MatchPage />} />
    </Routes>
  );
}
