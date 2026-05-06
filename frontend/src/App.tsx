import { Navigate, Route, Routes } from "react-router-dom";

function LobbyPage() {
  return <div>Lobby page (coming soon)</div>;
}

function AuctionPage() {
  return <div>Auction page (coming soon)</div>;
}

function MatchResultPage() {
  return <div>Match result page (coming soon)</div>;
}

export function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/lobby" replace />} />
      <Route path="/lobby" element={<LobbyPage />} />
      <Route path="/auction/:sessionId" element={<AuctionPage />} />
      <Route path="/match/:sessionId" element={<MatchResultPage />} />
    </Routes>
  );
}
