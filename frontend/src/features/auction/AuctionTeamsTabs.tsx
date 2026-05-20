import { useState } from "react";
import type { Formation, PlayerAuctionView, Position } from "../../shared/api/contracts";
import { AuctionRosterPanel } from "./AuctionRosterPanel";

const TEAM_STYLES = [
  { color: "#3b82f6", border: "#60a5fa" },
  { color: "#ef4444", border: "#f87171" },
  { color: "#a855f7", border: "#c084fc" },
  { color: "#22c55e", border: "#4ade80" },
] as const;

type Props = {
  players: PlayerAuctionView[];
  myUserId: string | null;
  myHighlightPositions?: Position[];
};

export function AuctionTeamsTabs({ players, myUserId, myHighlightPositions }: Props) {
  const ordered = [...players];
  const defaultId = myUserId ?? ordered[0]?.userId ?? "";
  const [selectedId, setSelectedId] = useState(defaultId);

  const activeId = ordered.some((p) => p.userId === selectedId) ? selectedId : defaultId;
  const selected = ordered.find((p) => p.userId === activeId) ?? ordered[0];
  if (!selected) return null;

  const selectedIndex = ordered.findIndex((p) => p.userId === selected.userId);
  const style = TEAM_STYLES[selectedIndex % TEAM_STYLES.length];
  const isMe = selected.userId === myUserId;
  const formation = selected.formation as Formation;

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        {ordered.map((p, index) => {
          const active = p.userId === activeId;
          const tabStyle = TEAM_STYLES[index % TEAM_STYLES.length];
          return (
            <button
              key={p.userId}
              type="button"
              onClick={() => setSelectedId(p.userId)}
              className={`text-sm font-medium px-3 py-2 rounded-lg border transition-colors ${
                active
                  ? "text-white ring-1"
                  : "bg-surface-elevated border-white/10 text-slate-300 hover:border-white/25"
              }`}
              style={
                active
                  ? { borderColor: tabStyle.border, backgroundColor: `${tabStyle.color}22` }
                  : undefined
              }
            >
              {p.nickname}
              {p.userId === myUserId && <span className="text-slate-400 font-normal ml-1">(you)</span>}
              <span className="text-slate-500 text-xs ml-1.5">
                {p.budgetRemaining}cr · {p.rosterSize}/11
              </span>
              {p.currentTurn && (
                <span className="ml-1.5 text-[10px] uppercase text-amber-300">· turn</span>
              )}
            </button>
          );
        })}
      </div>

      <AuctionRosterPanel
        title={isMe ? "My Team" : `${selected.nickname}'s Team`}
        formation={formation}
        cards={selected.team}
        teamColor={style.color}
        teamBorderColor={style.border}
        away={!isMe}
        highlightPositions={isMe ? myHighlightPositions : undefined}
      />
    </div>
  );
}
