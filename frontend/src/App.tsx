import { Navigate, Route, Routes } from "react-router-dom";
import { LobbyPage } from "./features/lobby/LobbyPage";
import { AuctionPage } from "./features/auction/AuctionPage";
import { MatchPage } from "./features/match/MatchPage";

export function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/lobby" replace />} />
      <Route path="/lobby" element={<LobbyPage />} />
      <Route path="/auction/:sessionId" element={<AuctionPage />} />
      <Route path="/match/:sessionId" element={<MatchPage />} />
    </Routes>
  );
}
