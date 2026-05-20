import { useEffect, useRef, useState, useCallback } from "react";
import type { MatchEvent } from "../../../shared/api/contracts";

interface MatchCommentaryProps {
  timeline: MatchEvent[];
  homeTeamId: string | null;
  onComplete: () => void;
  finalHomeScore: number;
  finalAwayScore: number;
  homeNickname: string;
  awayNickname: string;
}

const EVENT_INTERVAL_MS = 1200;

const EVENT_ICONS: Record<string, string> = {
  KICK_OFF: "🏟️",
  GOAL: "⚽",
  SHOT_SAVED: "🧤",
  SHOT_MISSED: "💨",
  FOUL: "🟨",
  HALF_TIME: "⏸️",
  FULL_TIME: "🏁",
  PENALTIES_START: "🎯",
  PENALTY_SCORED: "✅",
  PENALTY_MISSED: "❌",
  PENALTIES_END: "🏆",
};

const EVENT_COLORS: Record<string, string> = {
  KICK_OFF: "text-blue-400",
  GOAL: "text-yellow-400",
  SHOT_SAVED: "text-green-400",
  SHOT_MISSED: "text-gray-400",
  FOUL: "text-orange-400",
  HALF_TIME: "text-purple-400",
  FULL_TIME: "text-purple-400",
  PENALTIES_START: "text-cyan-400",
  PENALTY_SCORED: "text-green-400",
  PENALTY_MISSED: "text-red-400",
  PENALTIES_END: "text-amber-400",
};

function getCommentary(event: MatchEvent, isHome: boolean, homeNickname: string, awayNickname: string): string {
  const team = isHome ? homeNickname : awayNickname;
  const player = event.playerName || "A player";

  switch (event.type) {
    case "KICK_OFF":
      return event.minute === 0
        ? "The referee blows the whistle and we're underway!"
        : "Second half begins!";
    case "GOAL":
      return `GOAL! ${player} scores for ${team}! The crowd goes wild!`;
    case "SHOT_SAVED":
      return `${player} takes a shot but the goalkeeper makes a brilliant save!`;
    case "SHOT_MISSED":
      return `${player} fires wide! That was close!`;
    case "FOUL":
      return `Foul by ${player} (${team}). Free kick awarded.`;
    case "HALF_TIME":
      return "The referee blows for half time. Teams head to the dressing rooms.";
    case "FULL_TIME":
      return "Full time! The match is over!";
    case "PENALTIES_START":
      return "Penalties! The shoot-out decides who goes through.";
    case "PENALTY_SCORED":
      return `${player} scores from the spot for ${team}!`;
    case "PENALTY_MISSED":
      return `${player} misses! ${team} fail to convert.`;
    case "PENALTIES_END":
      return event.detail || "Penalty shoot-out complete!";
    default:
      return event.detail || "";
  }
}

export function MatchCommentary({
  timeline,
  homeTeamId,
  onComplete,
  finalHomeScore,
  finalAwayScore,
  homeNickname,
  awayNickname,
}: MatchCommentaryProps) {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [displayedEvents, setDisplayedEvents] = useState<MatchEvent[]>([]);
  const [homeScore, setHomeScore] = useState(0);
  const [awayScore, setAwayScore] = useState(0);
  const [homePens, setHomePens] = useState(0);
  const [awayPens, setAwayPens] = useState(0);
  const [inPenalties, setInPenalties] = useState(false);
  const [isComplete, setIsComplete] = useState(false);
  const completedRef = useRef(false);

  const feedRef = useRef<HTMLDivElement>(null);
  const intervalRef = useRef<number | null>(null);

  const processNextEvent = useCallback(() => {
    if (currentIndex >= timeline.length) {
      setHomeScore(finalHomeScore);
      setAwayScore(finalAwayScore);
      setIsComplete(true);
      return;
    }

    const event = timeline[currentIndex];
    setDisplayedEvents((prev) => [...prev, event]);

    if (event.type === "PENALTIES_START") {
      setInPenalties(true);
    }
    if (event.type === "GOAL") {
      if (event.teamId === homeTeamId) {
        setHomeScore((s) => s + 1);
      } else {
        setAwayScore((s) => s + 1);
      }
    }
    if (event.type === "PENALTY_SCORED") {
      if (event.teamId === homeTeamId) {
        setHomePens((s) => s + 1);
      } else {
        setAwayPens((s) => s + 1);
      }
    }

    setCurrentIndex((i) => i + 1);
  }, [currentIndex, timeline, homeTeamId, finalHomeScore, finalAwayScore]);

  useEffect(() => {
    if (timeline.length === 0 && !completedRef.current) {
      completedRef.current = true;
      onComplete();
    }
  }, [timeline.length, onComplete]);

  useEffect(() => {
    if (feedRef.current) {
      feedRef.current.scrollTop = feedRef.current.scrollHeight;
    }
  }, [displayedEvents]);

  useEffect(() => {
    if (isComplete || timeline.length === 0) {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
      return;
    }

    intervalRef.current = window.setInterval(processNextEvent, EVENT_INTERVAL_MS);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [isComplete, processNextEvent, timeline.length]);

  useEffect(() => {
    if (!isComplete) return;
    const timer = window.setTimeout(() => {
      if (completedRef.current) return;
      completedRef.current = true;
      onComplete();
    }, 1500);
    return () => window.clearTimeout(timer);
  }, [isComplete, onComplete]);

  const currentMinute =
    displayedEvents.length > 0 ? displayedEvents[displayedEvents.length - 1].minute : 0;

  return (
    <div className="bg-surface-card rounded-xl border border-white/10 overflow-hidden">
      <div className="bg-gradient-to-r from-blue-900/50 to-red-900/50 px-6 py-4">
        <div className="flex items-center justify-center gap-8">
          <div className="text-center">
            <p className="text-xs text-blue-300 mb-1 truncate max-w-24">{homeNickname}</p>
            <p className="text-4xl font-black text-white">{homeScore}</p>
            {inPenalties && <p className="text-xs text-cyan-300 mt-1">Pens {homePens}</p>}
          </div>
          <div className="flex flex-col items-center">
            <span className="text-2xl font-bold text-slate-400">-</span>
            <span className="text-xs text-slate-500 mt-1">{currentMinute}&apos;</span>
          </div>
          <div className="text-center">
            <p className="text-xs text-red-300 mb-1 truncate max-w-24">{awayNickname}</p>
            <p className="text-4xl font-black text-white">{awayScore}</p>
            {inPenalties && <p className="text-xs text-cyan-300 mt-1">Pens {awayPens}</p>}
          </div>
        </div>
      </div>

      <div ref={feedRef} className="h-80 overflow-y-auto p-4 space-y-3 bg-black/20">
        {displayedEvents.map((event, idx) => {
          const isHome = event.teamId === homeTeamId;
          const icon = EVENT_ICONS[event.type] || "📋";
          const colorClass = EVENT_COLORS[event.type] || "text-gray-400";
          const isGoal = event.type === "GOAL";

          return (
            <div key={idx} className={`flex gap-3 animate-fade-in ${isGoal ? "py-2" : ""}`}>
              <div className="w-10 flex-shrink-0 text-right">
                <span className="text-xs font-mono text-slate-500">{event.minute}&apos;</span>
              </div>
              <div className="flex-shrink-0">
                <span className={`text-lg ${isGoal ? "text-2xl animate-bounce" : ""}`}>{icon}</span>
              </div>
              <div className={`flex-1 ${isGoal ? "bg-yellow-500/10 -mx-2 px-2 py-1 rounded" : ""}`}>
                <p className={`text-sm ${colorClass} ${isGoal ? "font-bold text-base" : ""}`}>
                  {getCommentary(event, isHome, homeNickname, awayNickname)}
                </p>
                {isGoal && (
                  <div className="mt-1 flex items-center gap-2">
                    <span
                      className={`text-xs px-2 py-0.5 rounded ${
                        isHome ? "bg-blue-500/30 text-blue-300" : "bg-red-500/30 text-red-300"
                      }`}
                    >
                      {isHome ? homeNickname : awayNickname}
                    </span>
                    {event.playerName && (
                      <span className="text-xs text-yellow-400">Scorer: {event.playerName}</span>
                    )}
                  </div>
                )}
              </div>
            </div>
          );
        })}

        {!isComplete && (
          <div className="flex items-center gap-2 text-slate-500">
            <div className="w-10" />
            <div className="flex gap-1">
              <div className="w-2 h-2 bg-slate-500 rounded-full animate-bounce" />
              <div
                className="w-2 h-2 bg-slate-500 rounded-full animate-bounce"
                style={{ animationDelay: "0.1s" }}
              />
              <div
                className="w-2 h-2 bg-slate-500 rounded-full animate-bounce"
                style={{ animationDelay: "0.2s" }}
              />
            </div>
          </div>
        )}
      </div>

      <div className="px-4 py-3 bg-black/30 border-t border-white/10 text-center">
        <p className="text-sm text-slate-400">
          {isComplete ? "Match complete" : "Match simulation in progress…"}
        </p>
      </div>
    </div>
  );
}
