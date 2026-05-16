import { useEffect, useRef } from "react";
import type { MatchEvent } from "../../../shared/api/contracts";

interface EventFeedProps {
  events: MatchEvent[];
  homeTeamId: string | null;
  maxHeight?: string;
}

function getEventIcon(type: string): string {
  switch (type) {
    case "GOAL":
      return "\u26BD"; // Soccer ball
    case "SHOT_SAVED":
      return "\u{1F9E4}"; // Gloves (goalkeeper save)
    case "SHOT_MISSED":
      return "\u274C"; // X mark
    case "FOUL":
      return "\u{1F7E8}"; // Yellow square (card)
    case "KICK_OFF":
      return "\u{1F3C3}"; // Runner
    case "HALF_TIME":
      return "\u23F8"; // Pause
    case "FULL_TIME":
      return "\u{1F3C1}"; // Checkered flag
    default:
      return "\u{1F4CD}"; // Pin
  }
}

function getEventStyle(type: string): { bg: string; text: string; border: string } {
  switch (type) {
    case "GOAL":
      return { bg: "bg-yellow-900/30", text: "text-yellow-400", border: "border-yellow-500/30" };
    case "SHOT_SAVED":
      return { bg: "bg-blue-900/30", text: "text-blue-400", border: "border-blue-500/30" };
    case "SHOT_MISSED":
      return { bg: "bg-gray-800/30", text: "text-gray-400", border: "border-gray-500/30" };
    case "FOUL":
      return { bg: "bg-red-900/30", text: "text-red-400", border: "border-red-500/30" };
    case "HALF_TIME":
    case "FULL_TIME":
      return { bg: "bg-purple-900/30", text: "text-purple-400", border: "border-purple-500/30" };
    case "KICK_OFF":
      return { bg: "bg-green-900/30", text: "text-green-400", border: "border-green-500/30" };
    default:
      return { bg: "bg-gray-800/30", text: "text-gray-400", border: "border-gray-500/30" };
  }
}

export function EventFeed({ events, homeTeamId, maxHeight = "200px" }: EventFeedProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom when new events are added
  useEffect(() => {
    if (containerRef.current) {
      containerRef.current.scrollTop = containerRef.current.scrollHeight;
    }
  }, [events.length]);

  // Reverse events to show most recent at bottom
  const sortedEvents = [...events].sort((a, b) => b.minute - a.minute);

  if (events.length === 0) {
    return (
      <div className="text-center text-gray-500 py-4 text-sm">
        No events yet
      </div>
    );
  }

  return (
    <div
      ref={containerRef}
      className="overflow-y-auto space-y-1 pr-1 scrollbar-thin scrollbar-thumb-gray-600 scrollbar-track-transparent"
      style={{ maxHeight }}
    >
      {sortedEvents.map((event, index) => {
        const style = getEventStyle(event.type);
        const icon = getEventIcon(event.type);
        const isHomeTeam = event.teamId === homeTeamId;

        return (
          <div
            key={`${event.minute}-${event.type}-${index}`}
            className={`flex items-start gap-2 p-2 rounded-lg border ${style.bg} ${style.border} transition-all duration-200`}
          >
            {/* Minute */}
            <div className="flex-shrink-0 w-8 text-right">
              <span className="text-xs font-bold text-gray-400">{event.minute}'</span>
            </div>

            {/* Icon */}
            <div className="flex-shrink-0 w-6 text-center">
              <span className="text-sm">{icon}</span>
            </div>

            {/* Content */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-1.5">
                {/* Team indicator */}
                {event.teamId && (
                  <div
                    className={`w-2 h-2 rounded-full flex-shrink-0 ${
                      isHomeTeam ? "bg-blue-500" : "bg-red-500"
                    }`}
                    title={isHomeTeam ? "Home" : "Away"}
                  />
                )}

                {/* Event type */}
                <span className={`text-xs font-semibold uppercase ${style.text}`}>
                  {event.type.replace(/_/g, " ")}
                </span>
              </div>

              {/* Player name & detail */}
              {(event.playerName || event.detail) && (
                <div className="text-xs text-gray-300 mt-0.5 truncate">
                  {event.playerName && (
                    <span className="font-medium">{event.playerName}</span>
                  )}
                  {event.playerName && event.detail && " - "}
                  {event.detail && (
                    <span className="text-gray-400">{event.detail}</span>
                  )}
                </div>
              )}
            </div>
          </div>
        );
      })}
    </div>
  );
}
