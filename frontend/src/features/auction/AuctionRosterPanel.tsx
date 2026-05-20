import { TeamPitch } from "../../shared/components/TeamPitch";
import { CardClubLabel } from "../../shared/components/CardClubLabel";
import type { Formation, Position } from "../../shared/api/contracts";
import type { AuctionTeamCardResponse } from "../../shared/api";
import { POSITION_COLORS } from "./hooks/useAuctionPageState";

type Props = {
  title: string;
  formation: Formation;
  cards: AuctionTeamCardResponse[];
  teamColor: string;
  teamBorderColor: string;
  away?: boolean;
  highlightPositions?: Position[];
};

export function AuctionRosterPanel({
  title,
  formation,
  cards,
  teamColor,
  teamBorderColor,
  away,
  highlightPositions,
}: Props) {
  return (
    <div className="bg-surface-card rounded-xl border border-white/10 overflow-hidden">
      <div className="px-4 py-3 border-b border-white/10 flex items-center justify-between">
        <h3 className="font-bold text-white">{title}</h3>
        <span className="text-sm text-slate-400">{cards.length}/11</span>
      </div>
      <div className="p-2">
        <TeamPitch
          formation={formation}
          cards={cards}
          away={away}
          teamColor={teamColor}
          teamBorderColor={teamBorderColor}
          highlightPositions={highlightPositions}
        />
      </div>
      <div className="px-4 py-2 border-t border-white/10 max-h-40 overflow-y-auto">
        {cards.length === 0 ? (
          <p className="text-slate-500 text-sm text-center py-2">No players yet</p>
        ) : (
          <div className="space-y-1">
            {cards.map((c) => (
              <div key={c.cardId} className="flex items-center justify-between text-sm">
                <div className="flex items-center gap-2">
                  <span
                    className={`${POSITION_COLORS[c.position] ?? "bg-gray-500"} text-white text-xs px-1.5 py-0.5 rounded font-bold`}
                  >
                    {c.position}
                  </span>
                  <span className="text-white">{c.cardName}</span>
                  <CardClubLabel clubName={c.clubName} />
                  <span className="text-slate-500">{c.rating}</span>
                </div>
                <span className="text-slate-400">{c.amountCents} cr</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
