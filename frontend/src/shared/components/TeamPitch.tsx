import type { Formation, AuctionTeamCardResponse, Position } from "../api/contracts";
import { FORMATION_DOTS } from "../formations";

interface TeamPitchProps {
  formation: Formation;
  cards: AuctionTeamCardResponse[];
  away?: boolean;
  teamColor?: string;
  teamBorderColor?: string;
  highlightPosition?: Position;
  showAmount?: boolean;
}

const HIGHLIGHT_BG = "#fbbf24";

interface SlotData {
  x: number;
  y: number;
  pos: Position;
  card: AuctionTeamCardResponse | null;
  highlighted: boolean;
}

function buildSlots(
  formation: Formation,
  cards: AuctionTeamCardResponse[],
  highlightPosition?: Position,
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
    return {
      x: dot.x,
      y: dot.y,
      pos: dot.pos,
      card,
      highlighted: !card && highlightPosition === dot.pos,
    };
  });
}

export function TeamPitch({
  formation,
  cards,
  teamColor = "#ffffff",
  highlightPosition,
  showAmount,
}: TeamPitchProps) {
  const slots = buildSlots(formation, cards, highlightPosition);

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
      <div className="absolute left-[8%] right-[8%] top-1/2 border-t border-white/15" />
      <div
        className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 border border-white/15 rounded-full"
        style={{ width: "22%", aspectRatio: "1 / 1" }}
      />
      <div className="absolute left-[25%] right-[25%] bottom-0 h-[14%] border border-b-0 border-white/15 rounded-t-sm" />
      <div className="absolute left-[25%] right-[25%] top-0 h-[14%] border border-t-0 border-white/15 rounded-b-sm" />

      {/* Player slots -- all sizes use cqw so they scale with the pitch */}
      {slots.map((slot, i) => {
        const filled = !!slot.card;
        const bg = slot.highlighted
          ? HIGHLIGHT_BG
          : filled
            ? teamColor
            : "rgba(255,255,255,0.15)";
        const borderColor = slot.highlighted
          ? "#f59e0b"
          : filled
            ? teamColor
            : "rgba(255,255,255,0.3)";

        return (
          <div
            key={i}
            className="absolute flex flex-col items-center pointer-events-none"
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
                  fontSize: "clamp(7px, 2.6cqw, 12px)",
                  lineHeight: 1,
                  marginBottom: "clamp(1px, 0.4cqw, 3px)",
                  padding: "0 clamp(2px, 0.6cqw, 4px)",
                  borderRadius: "clamp(1px, 0.3cqw, 3px)",
                  color: "#fff",
                  backgroundColor: "rgba(0,0,0,0.6)",
                  fontWeight: 900,
                }}
              >
                {slot.card!.rating}
              </span>
            )}

            {/* Player circle */}
            <div
              style={{
                width: "clamp(16px, 7cqw, 34px)",
                height: "clamp(16px, 7cqw, 34px)",
                borderRadius: "50%",
                backgroundColor: bg,
                border: `clamp(1.5px, 0.5cqw, 2.5px) solid ${borderColor}`,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                boxShadow: "0 1px 3px rgba(0,0,0,0.3)",
              }}
            >
              <span
                style={{
                  fontSize: "clamp(6px, 2cqw, 10px)",
                  fontWeight: 700,
                  color: filled || slot.highlighted ? "#111" : "rgba(255,255,255,0.4)",
                }}
              >
                {(filled ? slot.card!.position : slot.pos).slice(0, 2)}
              </span>
            </div>

            {/* Player name */}
            {filled && (
              <span
                style={{
                  fontSize: "clamp(6px, 2.2cqw, 11px)",
                  lineHeight: 1,
                  marginTop: "clamp(1px, 0.3cqw, 3px)",
                  padding: "clamp(1px, 0.2cqw, 2px) clamp(2px, 0.5cqw, 4px)",
                  borderRadius: "clamp(1px, 0.3cqw, 3px)",
                  color: "#fff",
                  backgroundColor: "rgba(0,0,0,0.6)",
                  fontWeight: 600,
                  maxWidth: "clamp(45px, 18cqw, 90px)",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                  whiteSpace: "nowrap",
                  textAlign: "center",
                }}
              >
                {slot.card!.cardName}
              </span>
            )}

            {/* Bid amount badge */}
            {filled && showAmount && (
              <span
                style={{
                  fontSize: "clamp(5px, 1.8cqw, 9px)",
                  lineHeight: 1,
                  marginTop: "clamp(0px, 0.2cqw, 2px)",
                  padding: "clamp(1px, 0.2cqw, 2px) clamp(2px, 0.4cqw, 4px)",
                  borderRadius: "clamp(1px, 0.3cqw, 3px)",
                  color: "#fbbf24",
                  backgroundColor: "rgba(0,0,0,0.7)",
                  fontWeight: 700,
                }}
              >
                {slot.card!.amountCents} cr
              </span>
            )}

            {/* Empty-slot highlight label */}
            {slot.highlighted && (
              <span
                style={{
                  fontSize: "clamp(6px, 2.2cqw, 11px)",
                  lineHeight: 1,
                  marginTop: "clamp(1px, 0.3cqw, 2px)",
                  color: "#fcd34d",
                  fontWeight: 600,
                }}
              >
                {slot.pos}
              </span>
            )}
          </div>
        );
      })}
    </div>
  );
}
