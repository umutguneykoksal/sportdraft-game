import { useMemo } from "react";
import type { Formation, AuctionTeamCardResponse, MatchEvent } from "../../../shared/api/contracts";
import { useFootballSimulation, PlayerPosition } from "../hooks/useFootballSimulation";

interface TeamPitch2DProps {
  homeFormation: Formation;
  awayFormation: Formation;
  homeCards: AuctionTeamCardResponse[];
  awayCards: AuctionTeamCardResponse[];
  currentEvent: MatchEvent | null;
  homeTeamId: string | null;
  currentMinute: number;
  isPlaying: boolean;
  seed?: number;
}

const HOME_COLOR = "#3b82f6";
const AWAY_COLOR = "#ef4444";
const BALL_SIZE = 14;

export function TeamPitch2D({
  homeFormation,
  awayFormation,
  homeCards,
  awayCards,
  currentEvent,
  homeTeamId,
  currentMinute,
  isPlaying,
  seed = 12345,
}: TeamPitch2DProps) {
  // Use the football simulation engine
  const simState = useFootballSimulation(
    homeFormation,
    awayFormation,
    homeCards,
    awayCards,
    isPlaying,
    seed
  );

  const isGoal = currentEvent?.type === "GOAL";
  const isShot = currentEvent?.type === "SHOT_SAVED" || currentEvent?.type === "SHOT_MISSED";

  // Find player with ball for highlighting
  const playerWithBall = useMemo(() => {
    return simState.players.find(p => p.hasBall);
  }, [simState.players]);

  return (
    <div
      className="relative w-full overflow-hidden rounded-lg"
      style={{
        containerType: "inline-size",
        aspectRatio: "3 / 4",
        background: "linear-gradient(180deg, #1a5c30 0%, #1e6e3a 50%, #1a5c30 100%)",
      }}
    >
      {/* Pitch markings */}
      <div className="absolute left-[8%] right-[8%] top-1/2 border-t border-white/20" />
      <div
        className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 border border-white/20 rounded-full"
        style={{ width: "22%", aspectRatio: "1 / 1" }}
      />
      {/* Home goal area (bottom) */}
      <div className="absolute left-[25%] right-[25%] bottom-0 h-[14%] border border-b-0 border-white/20 rounded-t-sm" />
      <div className="absolute left-[35%] right-[35%] bottom-0 h-[7%] border border-b-0 border-white/20 rounded-t-sm" />
      {/* Away goal area (top) */}
      <div className="absolute left-[25%] right-[25%] top-0 h-[14%] border border-t-0 border-white/20 rounded-b-sm" />
      <div className="absolute left-[35%] right-[35%] top-0 h-[7%] border border-t-0 border-white/20 rounded-b-sm" />

      {/* Goal celebration effect */}
      {isGoal && (
        <>
          <div className="absolute inset-0 animate-pulse bg-yellow-400/30 pointer-events-none" />
          <div className="absolute inset-0 pointer-events-none overflow-hidden">
            {[...Array(12)].map((_, i) => (
              <div
                key={i}
                className="absolute w-2 h-2 rounded-full animate-bounce"
                style={{
                  left: `${10 + (i * 7)}%`,
                  top: `${20 + (i % 3) * 20}%`,
                  backgroundColor: ['#fbbf24', '#ef4444', '#3b82f6', '#22c55e'][i % 4],
                  animationDelay: `${i * 0.1}s`,
                  animationDuration: '0.5s',
                }}
              />
            ))}
          </div>
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <div className="text-6xl font-black text-yellow-400 animate-ping opacity-50">
              GOAL!
            </div>
          </div>
        </>
      )}

      {/* Pass/Shot line */}
      {simState.passLine && (
        <svg className="absolute inset-0 w-full h-full pointer-events-none z-5">
          <line
            x1={`${simState.passLine.fromX}%`}
            y1={`${simState.passLine.fromY}%`}
            x2={`${simState.passLine.toX}%`}
            y2={`${simState.passLine.toY}%`}
            stroke="rgba(255,255,255,0.5)"
            strokeWidth="2"
            strokeDasharray="4,4"
          />
        </svg>
      )}
      {isShot && currentEvent && (
        <svg className="absolute inset-0 w-full h-full pointer-events-none">
          <line
            x1={`${simState.ballX}%`}
            y1={`${simState.ballY}%`}
            x2="50%"
            y2={currentEvent.teamId === homeTeamId ? "5%" : "95%"}
            stroke="rgba(255,255,255,0.6)"
            strokeWidth="2"
            strokeDasharray="5,5"
            className="animate-pulse"
          />
        </svg>
      )}

      {/* Players */}
      {simState.players.map((player) => {
        const teamColor = player.isHome ? HOME_COLOR : AWAY_COLOR;
        const isActive = player.hasBall || player.id === currentEvent?.playerId;
        const borderColor = isActive ? "#fbbf24" : teamColor;

        return (
          <div
            key={player.id}
            className={`absolute flex flex-col items-center pointer-events-none ${
              isActive ? "z-10" : ""
            }`}
            style={{
              left: `${player.x}%`,
              top: `${player.y}%`,
              transform: `translate(-50%, -50%) ${isActive ? "scale(1.15)" : "scale(1)"}`,
              transition: "left 0.1s linear, top 0.1s linear, transform 0.15s ease-out",
            }}
          >
            {/* Player circle */}
            <div
              style={{
                width: "clamp(12px, 5cqw, 24px)",
                height: "clamp(12px, 5cqw, 24px)",
                borderRadius: "50%",
                backgroundColor: teamColor,
                border: `clamp(1.5px, 0.4cqw, 2px) solid ${borderColor}`,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: isActive
                  ? "0 0 12px rgba(251, 191, 36, 0.8)"
                  : "0 1px 2px rgba(0,0,0,0.3)",
                transition: "box-shadow 0.15s ease-out",
              }}
            >
              <span
                style={{
                  fontSize: "clamp(5px, 1.6cqw, 8px)",
                  fontWeight: 700,
                  color: "#fff",
                }}
              >
                {player.position.slice(0, 2)}
              </span>
            </div>

            {/* Player name (only show for active player) */}
            {isActive && (
              <span
                style={{
                  fontSize: "clamp(5px, 1.8cqw, 9px)",
                  lineHeight: 1,
                  marginTop: "clamp(1px, 0.2cqw, 2px)",
                  padding: "clamp(1px, 0.15cqw, 2px) clamp(2px, 0.4cqw, 3px)",
                  borderRadius: "clamp(1px, 0.2cqw, 2px)",
                  color: "#fff",
                  backgroundColor: "rgba(0,0,0,0.8)",
                  fontWeight: 600,
                  maxWidth: "clamp(40px, 15cqw, 70px)",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                  textAlign: "center",
                }}
              >
                {player.name}
              </span>
            )}
          </div>
        );
      })}

      {/* Ball */}
      <div
        className="absolute pointer-events-none z-20"
        style={{
          left: `${simState.ballX}%`,
          top: `${simState.ballY}%`,
          transform: "translate(-50%, -50%)",
          width: BALL_SIZE,
          height: BALL_SIZE,
          transition: "left 0.1s linear, top 0.1s linear",
        }}
      >
        <div
          className={`w-full h-full rounded-full ${isGoal ? "animate-bounce" : ""}`}
          style={{
            background: "radial-gradient(circle at 30% 30%, #fff 0%, #e5e5e5 50%, #ccc 100%)",
            boxShadow: "0 2px 4px rgba(0,0,0,0.4), inset 0 1px 2px rgba(255,255,255,0.8)",
          }}
        />
      </div>

      {/* Score display */}
      <div className="absolute top-2 left-1/2 -translate-x-1/2 z-30">
        <div className="bg-black/60 px-3 py-1 rounded-full flex items-center gap-2">
          <div className="w-2 h-2 rounded-full" style={{ backgroundColor: HOME_COLOR }} />
          <span className="text-white font-bold text-sm">{simState.homeScore}</span>
          <span className="text-white/50 text-xs">-</span>
          <span className="text-white font-bold text-sm">{simState.awayScore}</span>
          <div className="w-2 h-2 rounded-full" style={{ backgroundColor: AWAY_COLOR }} />
        </div>
      </div>

      {/* Event popup */}
      {currentEvent && (
        <div className="absolute bottom-4 left-1/2 -translate-x-1/2 z-30 animate-bounce">
          <div
            className={`px-3 py-1.5 rounded-lg text-white font-bold text-sm shadow-lg ${
              isGoal
                ? "bg-yellow-500"
                : isShot
                  ? "bg-blue-500"
                  : currentEvent.type === "FOUL"
                    ? "bg-red-500"
                    : "bg-gray-700"
            }`}
          >
            <span className="mr-1">{currentEvent.minute}'</span>
            {currentEvent.type === "GOAL" && "GOAL!"}
            {currentEvent.type === "SHOT_SAVED" && "SAVED!"}
            {currentEvent.type === "SHOT_MISSED" && "MISSED!"}
            {currentEvent.type === "FOUL" && "FOUL"}
            {currentEvent.type === "KICK_OFF" && "KICK OFF"}
            {currentEvent.type === "HALF_TIME" && "HALF TIME"}
            {currentEvent.type === "FULL_TIME" && "FULL TIME"}
            {currentEvent.playerName && (
              <span className="ml-1 font-normal">- {currentEvent.playerName}</span>
            )}
          </div>
        </div>
      )}

      {/* Last action log */}
      {simState.lastAction && isPlaying && (
        <div className="absolute bottom-12 left-2 right-2 z-20">
          <div className="bg-black/50 px-2 py-1 rounded text-white/80 text-xs truncate">
            {simState.lastAction}
          </div>
        </div>
      )}
    </div>
  );
}
