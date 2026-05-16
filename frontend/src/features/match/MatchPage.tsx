import { useState, useEffect } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMatchFlow } from "./hooks/useMatchFlow";
import { TeamPitch } from "../../shared/components/TeamPitch";
import { MatchCommentary } from "./components/MatchCommentary";
import { FORMATION_LABELS } from "../../shared/formations";
import { clearActiveSessionId } from "../../shared/session";
import { getMatchTimeline, MatchEvent } from "../../shared/api";

type ViewState = "loading" | "commentary" | "result";

export function MatchPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const {
    ready, homeScore, awayScore,
    myTeam, opponentTeam, myFormation, opponentFormation,
    myNickname, opponentNickname, myTeamId,
    myShots, opponentShots, myFouls, opponentFouls,
  } = useMatchFlow(sessionId);

  const [viewState, setViewState] = useState<ViewState>("loading");
  const [timeline, setTimeline] = useState<MatchEvent[]>([]);
  const [timelineLoading, setTimelineLoading] = useState(false);

  // Fetch timeline when match is ready
  useEffect(() => {
    if (ready && sessionId && timeline.length === 0 && !timelineLoading) {
      setTimelineLoading(true);
      getMatchTimeline(sessionId)
        .then((data) => {
          setTimeline(data);
          // If we have events, show commentary; otherwise go straight to result
          setViewState(data.length > 0 ? "commentary" : "result");
        })
        .catch((err) => {
          console.error("Failed to load timeline:", err);
          setViewState("result");
        })
        .finally(() => setTimelineLoading(false));
    }
  }, [ready, sessionId, timeline.length, timelineLoading]);

  // Use actual team ID from backend for timeline event matching
  const homeTeamId = myTeamId;

  const isWin = ready && homeScore !== null && awayScore !== null && homeScore > awayScore;
  const isLoss = ready && homeScore !== null && awayScore !== null && homeScore < awayScore;

  const handleCommentaryComplete = () => {
    setViewState("result");
  };

  return (
    <div className="min-h-screen bg-surface flex flex-col">
      {/* Header */}
      <div className="bg-surface-card border-b border-white/10 px-4 py-4">
        <div className="max-w-6xl mx-auto flex items-center justify-between">
          <h1 className="text-2xl font-extrabold text-white">
            {viewState === "commentary" ? "Match In Progress" : "Match Result"}
          </h1>
        </div>
      </div>

      {/* Loading State */}
      {(viewState === "loading" || (!ready && viewState !== "result")) && (
        <div className="flex-1 flex items-center justify-center">
          <div className="animate-pulse text-center">
            <p className="text-xl text-slate-400">
              {timelineLoading ? "Loading match..." : "Waiting for match result..."}
            </p>
            <div className="mt-4 flex justify-center gap-3">
              <div className="w-3 h-3 bg-accent rounded-full animate-bounce"></div>
              <div className="w-3 h-3 bg-accent rounded-full animate-bounce" style={{ animationDelay: "0.1s" }}></div>
              <div className="w-3 h-3 bg-accent rounded-full animate-bounce" style={{ animationDelay: "0.2s" }}></div>
            </div>
          </div>
        </div>
      )}

      {/* Commentary View */}
      {viewState === "commentary" && ready && timeline.length > 0 && (
        <div className="flex-1 max-w-3xl mx-auto px-4 py-6 w-full">
          <MatchCommentary
            timeline={timeline}
            homeTeamId={homeTeamId}
            onComplete={handleCommentaryComplete}
            finalHomeScore={homeScore ?? 0}
            finalAwayScore={awayScore ?? 0}
            myNickname={myNickname}
            opponentNickname={opponentNickname}
          />
        </div>
      )}

      {/* Final Result View */}
      {viewState === "result" && ready && (
        <>
          {/* Scoreboard */}
          <div className="bg-surface-card border-b border-white/10">
            <div className="max-w-2xl mx-auto px-4 py-8 text-center">
              <div className="flex items-center justify-center gap-8">
                <div className="text-center">
                  <p className="text-sm text-slate-400 mb-1 truncate max-w-32">{myNickname}</p>
                  <p className={`text-7xl font-black ${isWin ? "text-accent-green" : isLoss ? "text-red-400" : "text-white"}`}>
                    {homeScore}
                  </p>
                </div>
                <div className="text-3xl font-bold text-slate-500">-</div>
                <div className="text-center">
                  <p className="text-sm text-slate-400 mb-1 truncate max-w-32">{opponentNickname}</p>
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
            </div>
          </div>

          {/* Match Stats Summary */}
          {(myShots > 0 || opponentShots > 0 || myFouls > 0 || opponentFouls > 0) && (
            <div className="max-w-2xl mx-auto px-4 py-4">
              <div className="bg-surface-card rounded-xl border border-white/10 p-4">
                <h3 className="font-bold text-white mb-3 text-center">Match Statistics</h3>
                <div className="grid grid-cols-3 gap-4 text-center">
                  <div>
                    <p className="text-2xl font-bold text-blue-400">{myShots}</p>
                    <p className="text-xs text-slate-400">Shots</p>
                  </div>
                  <div>
                    <p className="text-sm text-slate-500">vs</p>
                  </div>
                  <div>
                    <p className="text-2xl font-bold text-red-400">{opponentShots}</p>
                    <p className="text-xs text-slate-400">Shots</p>
                  </div>
                </div>
                <div className="grid grid-cols-3 gap-4 text-center mt-3">
                  <div>
                    <p className="text-2xl font-bold text-blue-400">{myFouls}</p>
                    <p className="text-xs text-slate-400">Fouls</p>
                  </div>
                  <div>
                    <p className="text-sm text-slate-500">vs</p>
                  </div>
                  <div>
                    <p className="text-2xl font-bold text-red-400">{opponentFouls}</p>
                    <p className="text-xs text-slate-400">Fouls</p>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Team Pitches */}
          <div className="flex-1 max-w-6xl mx-auto px-4 py-6 w-full">
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* My Team */}
              <div className={`bg-surface-card rounded-xl border overflow-hidden ${
                isWin ? "border-accent-green/50" : "border-white/10"
              }`}>
                <div className="px-4 py-3 border-b border-white/10 flex items-center justify-between">
                  <h3 className="font-bold text-white truncate max-w-40">{myNickname}</h3>
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
                  <h3 className="font-bold text-white truncate max-w-40">{opponentNickname}</h3>
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
                onClick={() => {
                  clearActiveSessionId();
                  navigate("/lobby");
                }}
                className="bg-accent hover:bg-blue-600 text-white font-bold py-3 px-8 rounded-xl text-lg transition-colors"
              >
                Back to Lobby
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
