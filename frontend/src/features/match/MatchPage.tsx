import { useState, useEffect } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useMatchFlow } from "./hooks/useMatchFlow";
import { useMatchPlayback } from "./hooks/useMatchPlayback";
import { TeamPitch } from "../../shared/components/TeamPitch";
import { TeamPitch2D } from "./components/TeamPitch2D";
import { EventFeed } from "./components/EventFeed";
import { PlaybackControls } from "./components/PlaybackControls";
import { FORMATION_LABELS } from "../../shared/formations";
import { clearActiveSessionId } from "../../shared/session";
import { getMatchTimeline, MatchEvent } from "../../shared/api";

type Tab = "result" | "replay";

export function MatchPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const {
    ready, homeScore, awayScore,
    myTeam, opponentTeam, myFormation, opponentFormation,
  } = useMatchFlow(sessionId);

  const [activeTab, setActiveTab] = useState<Tab>("result");
  const [timeline, setTimeline] = useState<MatchEvent[]>([]);
  const [timelineLoading, setTimelineLoading] = useState(false);

  // Fetch timeline when switching to replay tab
  useEffect(() => {
    if (activeTab === "replay" && sessionId && timeline.length === 0 && !timelineLoading) {
      setTimelineLoading(true);
      getMatchTimeline(sessionId)
        .then(setTimeline)
        .catch(console.error)
        .finally(() => setTimelineLoading(false));
    }
  }, [activeTab, sessionId, timeline.length, timelineLoading]);

  // For now, use sessionId as homeTeamId placeholder (we'd need to get actual team IDs)
  const homeTeamId = sessionId ?? null;

  const playback = useMatchPlayback(timeline, homeTeamId);

  const isWin = ready && homeScore !== null && awayScore !== null && homeScore > awayScore;
  const isLoss = ready && homeScore !== null && awayScore !== null && homeScore < awayScore;

  return (
    <div className="min-h-screen bg-surface flex flex-col">
      {/* Header */}
      <div className="bg-surface-card border-b border-white/10 px-4 py-4">
        <div className="max-w-6xl mx-auto flex items-center justify-between">
          <h1 className="text-2xl font-extrabold text-white">Match Result</h1>
          
          {/* Tab Switcher */}
          {ready && (
            <div className="flex bg-gray-800 rounded-lg p-1">
              <button
                onClick={() => setActiveTab("result")}
                className={`px-4 py-1.5 text-sm font-medium rounded-md transition-colors ${
                  activeTab === "result"
                    ? "bg-accent text-white"
                    : "text-gray-400 hover:text-white"
                }`}
              >
                Final Result
              </button>
              <button
                onClick={() => setActiveTab("replay")}
                className={`px-4 py-1.5 text-sm font-medium rounded-md transition-colors ${
                  activeTab === "replay"
                    ? "bg-accent text-white"
                    : "text-gray-400 hover:text-white"
                }`}
              >
                Match Replay
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Loading State */}
      {!ready && (
        <div className="flex-1 flex items-center justify-center">
          <div className="animate-pulse text-center">
            <p className="text-xl text-slate-400">Waiting for match result...</p>
            <div className="mt-4 flex justify-center gap-3">
              <div className="w-3 h-3 bg-accent rounded-full animate-bounce"></div>
              <div className="w-3 h-3 bg-accent rounded-full animate-bounce" style={{ animationDelay: "0.1s" }}></div>
              <div className="w-3 h-3 bg-accent rounded-full animate-bounce" style={{ animationDelay: "0.2s" }}></div>
            </div>
          </div>
        </div>
      )}

      {/* Final Result Tab */}
      {ready && activeTab === "result" && (
        <>
          {/* Scoreboard */}
          <div className="bg-surface-card border-b border-white/10">
            <div className="max-w-2xl mx-auto px-4 py-8 text-center">
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

      {/* Match Replay Tab */}
      {ready && activeTab === "replay" && (
        <div className="flex-1 max-w-6xl mx-auto px-4 py-6 w-full">
          {timelineLoading ? (
            <div className="text-center py-12">
              <div className="animate-spin w-8 h-8 border-2 border-accent border-t-transparent rounded-full mx-auto"></div>
              <p className="text-slate-400 mt-4">Loading match replay...</p>
            </div>
          ) : timeline.length === 0 ? (
            <div className="text-center py-12">
              <p className="text-slate-400">No replay data available for this match.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 lg:gap-6">
              {/* 2D Pitch - Takes 2 columns on large screens */}
              <div className="lg:col-span-2 order-1">
                <div className="bg-surface-card rounded-xl border border-white/10 overflow-hidden">
                  <TeamPitch2D
                    homeFormation={myFormation}
                    awayFormation={opponentFormation}
                    homeCards={myTeam}
                    awayCards={opponentTeam}
                    currentEvent={playback.currentEvent}
                    homeTeamId={homeTeamId}
                    currentMinute={playback.currentMinute}
                    isPlaying={playback.isPlaying}
                  />
                </div>

                {/* Playback Controls */}
                <div className="mt-3 lg:mt-4">
                  <PlaybackControls
                    currentMinute={playback.currentMinute}
                    isPlaying={playback.isPlaying}
                    speed={playback.speed}
                    isFinished={playback.isFinished}
                    homeScore={playback.homeScore}
                    awayScore={playback.awayScore}
                    onTogglePlay={playback.togglePlay}
                    onSpeedChange={playback.setPlaybackSpeed}
                    onSeek={playback.seekTo}
                    onRestart={playback.restart}
                  />
                </div>
              </div>

              {/* Event Feed and Stats - Side column on desktop, below on mobile */}
              <div className="lg:col-span-1 order-2 space-y-3 lg:space-y-4">
                {/* Quick Stats - Shows first on mobile */}
                <div className="bg-surface-card rounded-xl border border-white/10 p-3 lg:p-4 lg:order-2">
                  <h3 className="font-bold text-white mb-2 lg:mb-3 text-sm lg:text-base">Match Stats</h3>
                  <div className="grid grid-cols-3 gap-2 lg:space-y-2 lg:block text-xs lg:text-sm">
                    <div className="flex flex-col lg:flex-row lg:justify-between text-center lg:text-left">
                      <span className="text-gray-400">Goals</span>
                      <span className="text-white font-medium">
                        {timeline.filter(e => e.type === "GOAL" && e.teamId === homeTeamId).length} - {timeline.filter(e => e.type === "GOAL" && e.teamId !== homeTeamId).length}
                      </span>
                    </div>
                    <div className="flex flex-col lg:flex-row lg:justify-between text-center lg:text-left">
                      <span className="text-gray-400">Shots</span>
                      <span className="text-white font-medium">
                        {timeline.filter(e => (e.type === "GOAL" || e.type === "SHOT_SAVED" || e.type === "SHOT_MISSED") && e.teamId === homeTeamId).length} - {timeline.filter(e => (e.type === "GOAL" || e.type === "SHOT_SAVED" || e.type === "SHOT_MISSED") && e.teamId !== homeTeamId).length}
                      </span>
                    </div>
                    <div className="flex flex-col lg:flex-row lg:justify-between text-center lg:text-left">
                      <span className="text-gray-400">Fouls</span>
                      <span className="text-white font-medium">
                        {timeline.filter(e => e.type === "FOUL" && e.teamId === homeTeamId).length} - {timeline.filter(e => e.type === "FOUL" && e.teamId !== homeTeamId).length}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Event Feed */}
                <div className="bg-surface-card rounded-xl border border-white/10 p-3 lg:p-4 lg:order-1">
                  <h3 className="font-bold text-white mb-2 lg:mb-3 text-sm lg:text-base">Match Events</h3>
                  <EventFeed
                    events={playback.pastEvents}
                    homeTeamId={homeTeamId}
                    maxHeight="250px"
                  />
                </div>
              </div>
            </div>
          )}

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
      )}
    </div>
  );
}
