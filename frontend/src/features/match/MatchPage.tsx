import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMatchFlow } from "./hooks/useMatchFlow";
import { TeamPitch } from "../../shared/components/TeamPitch";
import { getAuctionState } from "../../shared/api";
import type { AuctionTeamCardResponse, Formation } from "../../shared/api/contracts";
import { FORMATION_LABELS } from "../../shared/formations";

export function MatchPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const { ready, homeScore, awayScore } = useMatchFlow(sessionId);

  const [myTeam, setMyTeam] = useState<AuctionTeamCardResponse[]>([]);
  const [opponentTeam, setOpponentTeam] = useState<AuctionTeamCardResponse[]>([]);
  const [myFormation, setMyFormation] = useState<Formation>("FOUR_FOUR_TWO");
  const [opponentFormation, setOpponentFormation] = useState<Formation>("FOUR_FOUR_TWO");

  useEffect(() => {
    if (!sessionId) return;
    let cancelled = false;
    (async () => {
      try {
        const state = await getAuctionState(sessionId);
        if (cancelled) return;
        setMyTeam(state.myTeam);
        setOpponentTeam(state.opponentTeam);
        if (state.myFormation) setMyFormation(state.myFormation);
        if (state.opponentFormation) setOpponentFormation(state.opponentFormation);
      } catch {
        // teams not critical for match display
      }
    })();
    return () => { cancelled = true; };
  }, [sessionId]);

  const isWin = ready && homeScore !== null && awayScore !== null && homeScore > awayScore;
  const isLoss = ready && homeScore !== null && awayScore !== null && homeScore < awayScore;
  const isDraw = ready && homeScore !== null && awayScore !== null && homeScore === awayScore;

  return (
    <div className="min-h-screen bg-surface flex flex-col">
      {/* Header */}
      <div className="bg-surface-card border-b border-white/10 px-4 py-4 text-center">
        <h1 className="text-2xl font-extrabold text-white">Match Result</h1>
      </div>

      {/* Scoreboard */}
      <div className="bg-surface-card border-b border-white/10">
        <div className="max-w-2xl mx-auto px-4 py-8 text-center">
          {!ready ? (
            <div className="animate-pulse">
              <p className="text-xl text-slate-400">Waiting for match result...</p>
              <div className="mt-4 flex justify-center gap-3">
                <div className="w-3 h-3 bg-accent rounded-full animate-bounce"></div>
                <div className="w-3 h-3 bg-accent rounded-full animate-bounce" style={{ animationDelay: "0.1s" }}></div>
                <div className="w-3 h-3 bg-accent rounded-full animate-bounce" style={{ animationDelay: "0.2s" }}></div>
              </div>
            </div>
          ) : (
            <>
              <div className="flex items-center justify-center gap-8">
                <div className="text-center">
                  <p className="text-sm text-slate-400 mb-1">You</p>
                  <p className={`text-7xl font-black ${isWin ? "text-accent-green" : isLoss ? "text-red-400" : "text-white"}`}>
                    {homeScore}
                  </p>
                </div>
                <div className="text-3xl font-bold text-slate-500">-</div>
                <div className="text-center">
                  <p className="text-sm text-slate-400 mb-1">Opponent</p>
                  <p className={`text-7xl font-black ${isLoss ? "text-accent-green" : isWin ? "text-red-400" : "text-white"}`}>
                    {awayScore}
                  </p>
                </div>
              </div>

              <div className={`mt-4 inline-block px-6 py-2 rounded-full font-bold text-lg ${
                isWin
                  ? "bg-accent-green/20 text-accent-green"
                  : isLoss
                    ? "bg-red-500/20 text-red-400"
                    : "bg-slate-500/20 text-slate-300"
              }`}>
                {isWin ? "Victory!" : isLoss ? "Defeat" : "Draw"}
              </div>
            </>
          )}
        </div>
      </div>

      {/* Team Pitches */}
      <div className="flex-1 max-w-6xl mx-auto px-4 py-6 w-full">
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* My Team */}
          <div className={`bg-surface-card rounded-xl border overflow-hidden ${
            isWin ? "border-accent-green/50" : "border-white/10"
          }`}>
            <div className="px-4 py-3 border-b border-white/10 flex items-center justify-between">
              <h3 className="font-bold text-white">Your Team</h3>
              <span className="text-sm text-slate-400">{FORMATION_LABELS[myFormation]}</span>
            </div>
            <div className="p-2">
              <TeamPitch
                formation={myFormation}
                cards={myTeam}
                teamColor="#3b82f6"
                teamBorderColor="#60a5fa"
                showAmount
              />
            </div>
            <div className="px-4 py-2 border-t border-white/10 max-h-48 overflow-y-auto">
              <div className="space-y-1">
                {myTeam.map((c) => (
                  <div key={c.cardId} className="flex items-center justify-between text-sm">
                    <div className="flex items-center gap-2">
                      <span className="text-slate-400 text-xs w-8">{c.position}</span>
                      <span className="text-white">{c.cardName}</span>
                      <span className="text-slate-500">{c.rating}</span>
                    </div>
                    <span className="text-slate-400">{c.amountCents} cr</span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Opponent Team */}
          <div className={`bg-surface-card rounded-xl border overflow-hidden ${
            isLoss ? "border-accent-green/50" : "border-white/10"
          }`}>
            <div className="px-4 py-3 border-b border-white/10 flex items-center justify-between">
              <h3 className="font-bold text-white">Opponent</h3>
              <span className="text-sm text-slate-400">{FORMATION_LABELS[opponentFormation]}</span>
            </div>
            <div className="p-2">
              <TeamPitch
                formation={opponentFormation}
                cards={opponentTeam}
                away
                teamColor="#ef4444"
                teamBorderColor="#f87171"
                showAmount
              />
            </div>
            <div className="px-4 py-2 border-t border-white/10 max-h-48 overflow-y-auto">
              <div className="space-y-1">
                {opponentTeam.map((c) => (
                  <div key={c.cardId} className="flex items-center justify-between text-sm">
                    <div className="flex items-center gap-2">
                      <span className="text-slate-400 text-xs w-8">{c.position}</span>
                      <span className="text-white">{c.cardName}</span>
                      <span className="text-slate-500">{c.rating}</span>
                    </div>
                    <span className="text-slate-400">{c.amountCents} cr</span>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Back to Lobby */}
        <div className="text-center mt-8">
          <button
            onClick={() => navigate("/lobby")}
            className="bg-accent hover:bg-blue-600 text-white font-bold py-3 px-8 rounded-xl text-lg transition-colors"
          >
            Back to Lobby
          </button>
        </div>
      </div>
    </div>
  );
}
