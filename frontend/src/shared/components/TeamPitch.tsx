import SoccerLineUp, { type Team, type Player, type Squad } from "react-soccer-lineup";
import type { Formation, AuctionTeamCardResponse, Position } from "../api/contracts";
import { FORMATION_SLOTS, positionToSquadKey } from "../formations";

interface TeamPitchProps {
  formation: Formation;
  cards: AuctionTeamCardResponse[];
  /** Pitch color */
  color?: string;
  /** Size of the pitch */
  size?: "small" | "normal" | "responsive" | "fill";
  /** Show as away team (right half in horizontal) */
  away?: boolean;
  /** Team accent color */
  teamColor?: string;
  /** Team border color */
  teamBorderColor?: string;
  /** Position currently being auctioned -- slot glows */
  highlightPosition?: Position;
  orientation?: "horizontal" | "vertical";
}

const EMPTY_STYLE = {
  color: "rgba(255,255,255,0.15)",
  borderColor: "rgba(255,255,255,0.3)",
  numberColor: "rgba(255,255,255,0.4)",
  nameColor: "rgba(255,255,255,0.4)",
};

const HIGHLIGHT_STYLE = {
  color: "#fbbf24",
  borderColor: "#f59e0b",
  numberColor: "#1a1a2e",
  nameColor: "#fbbf24",
  nameBackgroundColor: "rgba(0,0,0,0.6)",
};

function buildSquad(
  formation: Formation,
  cards: AuctionTeamCardResponse[],
  highlightPosition?: Position
): Squad {
  const slots = FORMATION_SLOTS[formation];

  const byPosition: Record<string, AuctionTeamCardResponse[]> = {
    GK: [],
    DEF: [],
    MID: [],
    FWD: [],
  };
  for (const card of cards) {
    if (byPosition[card.position]) {
      byPosition[card.position].push(card);
    }
  }

  const makePlayer = (card: AuctionTeamCardResponse): Player => ({
    name: card.cardName,
    number: card.rating,
  });

  const makeEmptyPlayer = (pos: Position): Player => ({
    name: pos,
    style:
      highlightPosition === pos
        ? HIGHLIGHT_STYLE
        : EMPTY_STYLE,
  });

  const fillRow = (pos: Position, count: number): (Player | null)[] => {
    const filled = byPosition[pos].map(makePlayer);
    const remaining = count - filled.length;
    for (let i = 0; i < remaining; i++) {
      filled.push(makeEmptyPlayer(pos));
    }
    return filled;
  };

  const gkCards = byPosition["GK"];
  const gk: Player =
    gkCards.length > 0 ? makePlayer(gkCards[0]) : makeEmptyPlayer("GK");

  return {
    gk,
    df: fillRow("DEF", slots.df),
    cm: fillRow("MID", slots.cm),
    fw: fillRow("FWD", slots.fw),
  };
}

export function TeamPitch({
  formation,
  cards,
  color = "#1a6b35",
  size = "responsive",
  away = false,
  teamColor = "#ffffff",
  teamBorderColor = "#ffffff",
  highlightPosition,
  orientation = "vertical",
}: TeamPitchProps) {
  const squad = buildSquad(formation, cards, highlightPosition);

  const team: Team = {
    squad,
    style: {
      color: teamColor,
      borderColor: teamBorderColor,
      numberColor: "#1a1a2e",
      nameColor: "#ffffff",
      nameBackgroundColor: "rgba(0,0,0,0.5)",
    },
  };

  const pitchProps = away
    ? { awayTeam: team }
    : { homeTeam: team };

  return (
    <SoccerLineUp
      size={size}
      color={color}
      pattern="lines"
      orientation={orientation}
      {...pitchProps}
    />
  );
}
