import { useMemo } from "react";
import type { Formation, AuctionTeamCardResponse, Position, MatchEvent } from "../../../shared/api/contracts";
import { FORMATION_DOTS } from "../../../shared/formations";

interface TeamPitch2DProps {
  homeFormation: Formation;
  awayFormation: Formation;
  homeCards: AuctionTeamCardResponse[];
  awayCards: AuctionTeamCardResponse[];
  currentEvent: MatchEvent | null;
  homeTeamId: string | null;
  currentMinute: number;
}

interface SlotData {
  x: number;
  y: number;
  pos: Position;
  card: AuctionTeamCardResponse | null;
  isHome: boolean;
  isActive: boolean;
}

const HOME_COLOR = "#3b82f6"; // Blue
const AWAY_COLOR = "#ef4444"; // Red
const BALL_SIZE = 12;

function buildSlots(
  formation: Formation,
  cards: AuctionTeamCardResponse[],
  isHome: boolean,
  activePlayerId: string | null
): SlotData[] {
  const dots = FORMATION_DOTS[formation];
  const pool: Record<Position, AuctionTeamCardResponse[]> = {
    GK: [],
    DEF: [],
    CDM: [],
    CM: [],
    CAM: [],
    FWD: [],
  };
  for (const c of cards) {
    pool[c.position as Position]?.push(c);
  }

  return dots.map((dot) => {
    const card = pool[dot.pos]?.shift() ?? null;
    // Flip Y for away team (they play on opposite half)
    const yPos = isHome ? dot.y : 100 - dot.y;
    return {
      x: dot.x,
      y: yPos,
      pos: dot.pos,
      card,
      isHome,
      isActive: card?.cardId === activePlayerId,
    };
  });
}

function getBallPosition(
  currentEvent: MatchEvent | null,
  homeTeamId: string | null,
  currentMinute: number
): { x: number; y: number } {
  // Default: center
  if (!currentEvent) {
    // Ball moves based on minute (simple animation)
    const phase = (currentMinute % 10) / 10;
    return {
      x: 30 + phase * 40,
      y: 40 + Math.sin(phase * Math.PI * 2) * 15,
    };
  }

  const isHomeTeam = currentEvent.teamId === homeTeamId;

  switch (currentEvent.type) {
    case "KICK_OFF":
      return { x: 50, y: 50 };
    case "HALF_TIME":
    case "FULL_TIME":
      return { x: 50, y: 50 };
    case "GOAL":
      // Ball in the goal area
      return isHomeTeam ? { x: 50, y: 8 } : { x: 50, y: 92 };
    case "SHOT_SAVED":
    case "SHOT_MISSED":
      // Ball near goal
      return isHomeTeam ? { x: 50, y: 15 } : { x: 50, y: 85 };
    case "FOUL":
      // Random position on attacking half
      return isHomeTeam ? { x: 50, y: 35 } : { x: 50, y: 65 };
    default:
      return { x: 50, y: 50 };
  }
}

export function TeamPitch2D({
  homeFormation,
  awayFormation,
  homeCards,
  awayCards,
  currentEvent,
  homeTeamId,
  currentMinute,
}: TeamPitch2DProps) {
  const activePlayerId = currentEvent?.playerId ?? null;

  const homeSlots = useMemo(
    () => buildSlots(homeFormation, homeCards, true, activePlayerId),
    [homeFormation, homeCards, activePlayerId]
  );

  const awaySlots = useMemo(
    () => buildSlots(awayFormation, awayCards, false, activePlayerId),
    [awayFormation, awayCards, activePlayerId]
  );

  const allSlots = [...homeSlots, ...awaySlots];
  const ballPos = getBallPosition(currentEvent, homeTeamId, currentMinute);

  const isGoal = currentEvent?.type === "GOAL";
  const isShot = currentEvent?.type === "SHOT_SAVED" || currentEvent?.type === "SHOT_MISSED";

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
          {/* Flash overlay */}
          <div className="absolute inset-0 animate-pulse bg-yellow-400/30 pointer-events-none" />
          
          {/* Confetti-like particles */}
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
          
          {/* Goal text burst */}
          <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
            <div className="text-6xl font-black text-yellow-400 animate-ping opacity-50">
              GOAL!
            </div>
          </div>
        </>
      )}

      {/* Shot line animation */}
      {isShot && currentEvent && (
        <svg className="absolute inset-0 w-full h-full pointer-events-none">
          <line
            x1={`${ballPos.x}%`}
            y1={`${ballPos.y}%`}
            x2="50%"
            y2={currentEvent.teamId === homeTeamId ? "5%" : "95%"}
            stroke="rgba(255,255,255,0.6)"
            strokeWidth="2"
            strokeDasharray="5,5"
            className="animate-pulse"
          />
        </svg>
      )}

      {/* Player slots */}
      {allSlots.map((slot, i) => {
        const filled = !!slot.card;
        const teamColor = slot.isHome ? HOME_COLOR : AWAY_COLOR;
        const bg = filled ? teamColor : "rgba(255,255,255,0.15)";
        const borderColor = slot.isActive
          ? "#fbbf24"
          : filled
            ? teamColor
            : "rgba(255,255,255,0.3)";

        return (
          <div
            key={i}
            className={`absolute flex flex-col items-center pointer-events-none transition-all duration-300 ${
              slot.isActive ? "scale-125 z-10" : ""
            }`}
            style={{
              left: `${slot.x}%`,
              top: `${slot.y}%`,
              transform: "translate(-50%, -50%)",
            }}
          >
            {/* Rating badge */}
            {filled && (
              <span
                style={{
                  fontSize: "clamp(6px, 2.2cqw, 10px)",
                  lineHeight: 1,
                  marginBottom: "clamp(1px, 0.3cqw, 2px)",
                  padding: "0 clamp(2px, 0.5cqw, 3px)",
                  borderRadius: "clamp(1px, 0.3cqw, 2px)",
                  color: "#fff",
                  backgroundColor: "rgba(0,0,0,0.7)",
                  fontWeight: 900,
                }}
              >
                {slot.card!.rating}
              </span>
            )}

            {/* Player circle */}
            <div
              className={`transition-all duration-300 ${slot.isActive ? "ring-2 ring-yellow-400 ring-offset-1" : ""}`}
              style={{
                width: "clamp(14px, 6cqw, 28px)",
                height: "clamp(14px, 6cqw, 28px)",
                borderRadius: "50%",
                backgroundColor: bg,
                border: `clamp(1.5px, 0.4cqw, 2px) solid ${borderColor}`,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: slot.isActive
                  ? "0 0 10px rgba(251, 191, 36, 0.6)"
                  : "0 1px 2px rgba(0,0,0,0.3)",
              }}
            >
              <span
                style={{
                  fontSize: "clamp(5px, 1.8cqw, 9px)",
                  fontWeight: 700,
                  color: filled ? "#fff" : "rgba(255,255,255,0.4)",
                }}
              >
                {(filled ? slot.card!.position : slot.pos).slice(0, 2)}
              </span>
            </div>

            {/* Player name */}
            {filled && (
              <span
                style={{
                  fontSize: "clamp(5px, 1.8cqw, 9px)",
                  lineHeight: 1,
                  marginTop: "clamp(1px, 0.2cqw, 2px)",
                  padding: "clamp(1px, 0.15cqw, 2px) clamp(2px, 0.4cqw, 3px)",
                  borderRadius: "clamp(1px, 0.2cqw, 2px)",
                  color: "#fff",
                  backgroundColor: "rgba(0,0,0,0.7)",
                  fontWeight: 600,
                  maxWidth: "clamp(40px, 15cqw, 70px)",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                  textAlign: "center",
                }}
              >
                {slot.card!.cardName}
              </span>
            )}
          </div>
        );
      })}

      {/* Ball */}
      <div
        className="absolute transition-all duration-500 ease-out pointer-events-none z-20"
        style={{
          left: `${ballPos.x}%`,
          top: `${ballPos.y}%`,
          transform: "translate(-50%, -50%)",
          width: BALL_SIZE,
          height: BALL_SIZE,
        }}
      >
        <div
          className={`w-full h-full rounded-full bg-white shadow-lg ${
            isGoal ? "animate-bounce" : ""
          }`}
          style={{
            background: "radial-gradient(circle at 30% 30%, #fff 0%, #e5e5e5 50%, #ccc 100%)",
            boxShadow: "0 2px 4px rgba(0,0,0,0.4), inset 0 1px 2px rgba(255,255,255,0.8)",
          }}
        />
      </div>

      {/* Event popup */}
      {currentEvent && (
        <div className="absolute top-4 left-1/2 -translate-x-1/2 z-30 animate-bounce">
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
    </div>
  );
}
