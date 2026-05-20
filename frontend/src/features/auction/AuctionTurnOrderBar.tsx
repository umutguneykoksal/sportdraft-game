import type { LobbyPlayerView, PlayerAuctionView } from "../../shared/api/contracts";

export interface TurnOrderEntry {
  userId: string;
  nickname: string;
  isCurrentTurn: boolean;
  isMe: boolean;
  budgetRemaining?: number;
  rosterSize?: number;
}

export function buildTurnOrderEntries(
  playerViews: PlayerAuctionView[],
  lobbyPlayers: LobbyPlayerView[] | undefined,
  currentTurnUserId: string | null | undefined,
  myUserId: string | null
): TurnOrderEntry[] {
  if (playerViews.length > 0) {
    return playerViews.map((p) => ({
      userId: p.userId,
      nickname: p.nickname,
      isCurrentTurn: p.currentTurn,
      isMe: p.userId === myUserId,
      budgetRemaining: p.budgetRemaining,
      rosterSize: p.rosterSize,
    }));
  }
  const sorted = [...(lobbyPlayers ?? [])].sort((a, b) => a.seat - b.seat);
  return sorted.map((p) => ({
    userId: p.userId,
    nickname: p.nickname,
    isCurrentTurn: currentTurnUserId === p.userId,
    isMe: p.userId === myUserId,
  }));
}

/** 2-player DUEL: turn order from lobby seats with budgets from auction teams. */
export function buildDuelTurnOrderEntries(
  lobbyPlayers: LobbyPlayerView[] | undefined,
  currentTurnUserId: string | null | undefined,
  myUserId: string | null,
  myBudget: number,
  myRosterSize: number,
  opponentBudget: number,
  opponentRosterSize: number
): TurnOrderEntry[] {
  const sorted = [...(lobbyPlayers ?? [])].sort((a, b) => a.seat - b.seat);
  if (sorted.length < 2) return [];

  return sorted.map((p) => {
    const isMe = p.userId === myUserId;
    return {
      userId: p.userId,
      nickname: p.nickname,
      isCurrentTurn: currentTurnUserId === p.userId,
      isMe,
      budgetRemaining: isMe ? myBudget : opponentBudget,
      rosterSize: isMe ? myRosterSize : opponentRosterSize,
    };
  });
}

export function AuctionTurnOrderBar({
  entries,
  turnIsOpen = false,
}: {
  entries: TurnOrderEntry[];
  turnIsOpen?: boolean;
}) {
  if (entries.length < 2) return null;

  return (
    <div className="border-b border-white/10 bg-surface-card px-4 py-3">
      <div className="max-w-7xl mx-auto">
        {turnIsOpen && (
          <p className="text-xs text-amber-300 bg-amber-500/10 border border-amber-500/30 rounded-lg px-3 py-1.5 mb-2 text-center">
            Open bidding — any player with a slot for this card may bid
          </p>
        )}
        <p className="text-[10px] uppercase tracking-wider text-slate-500 mb-2">Bid order</p>
        <div className="flex items-center gap-1 sm:gap-2 overflow-x-auto pb-1">
          {entries.map((entry, index) => (
            <div key={entry.userId} className="flex items-center gap-1 sm:gap-2 shrink-0">
              {index > 0 && (
                <span className="text-slate-600 text-sm select-none" aria-hidden>
                  →
                </span>
              )}
              <div
                className={`rounded-lg border px-3 py-2 min-w-[120px] sm:min-w-[140px] transition-colors ${
                  entry.isCurrentTurn
                    ? "border-accent bg-accent/10 ring-1 ring-accent/40"
                    : "border-white/10 bg-surface-elevated/50"
                }`}
                title={entry.isCurrentTurn ? "Current turn" : undefined}
              >
                <div className="flex items-center gap-1.5 mb-0.5">
                  <span className="text-[10px] font-bold text-slate-500 w-4">{index + 1}</span>
                  <p className="text-xs sm:text-sm font-semibold text-white truncate max-w-[100px] sm:max-w-[120px]">
                    {entry.nickname}
                    {entry.isMe && (
                      <span className="text-accent font-normal ml-1">(you)</span>
                    )}
                  </p>
                </div>
                {entry.budgetRemaining != null && (
                  <p className="text-[11px] text-slate-400 pl-5">
                    {entry.budgetRemaining} cr · {entry.rosterSize ?? 0}/11
                  </p>
                )}
                {entry.isCurrentTurn && (
                  <p className="text-[10px] text-accent mt-1 pl-5 font-medium">Bidding now</p>
                )}
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
