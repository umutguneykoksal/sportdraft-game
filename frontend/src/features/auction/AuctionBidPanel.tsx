import { ROUND_STATUS_LABELS } from "../../shared/api/contracts";
import { PositionBadgeRow } from "../../shared/components/PositionBadgeRow";
import { PlayerAttributeBars } from "../../shared/components/PlayerAttributeBars";
import { POSITION_COLORS } from "./hooks/useAuctionPageState";
import { CardClubLabel } from "../../shared/components/CardClubLabel";
import type { AuctionRoundResponse } from "../../shared/api";

export type AuctionBidPanelProps = {
  round: AuctionRoundResponse | null;
  auctionPhaseActive: boolean;
  error: string | null;
  secondsLeft: number;
  timeExtended: boolean;
  timerPercent: number;
  timerColor: string;
  minimumBid: number;
  budget: number;
  roundIsOpen: boolean;
  controlsLocked: boolean;
  loading: boolean;
  canBid: boolean;
  couldBid: boolean;
  canPass: boolean;
  canFillCard: boolean;
  showTakePanel: boolean;
  showNoSlotBanner: boolean;
  iAmHighestBidder: boolean;
  opponentHasBid: boolean;
  mySquadFull: boolean;
  myUserId: string | null;
  turnIsOpen: boolean;
  isMyTurn: boolean;
  inTimerGrace: boolean;
  amount: string;
  setAmount: (v: string) => void;
  turnStatusMessage: () => string;
  onTake: () => void;
  onBid: () => void;
  onPass: () => void;
};

export function AuctionBidPanel(props: AuctionBidPanelProps) {
  const {
    round,
    auctionPhaseActive,
    error,
    secondsLeft,
    timeExtended,
    timerPercent,
    timerColor,
    minimumBid,
    budget,
    roundIsOpen,
    controlsLocked,
    loading,
    canBid,
    couldBid,
    canPass,
    canFillCard,
    showTakePanel,
    showNoSlotBanner,
    iAmHighestBidder,
    opponentHasBid,
    mySquadFull,
    myUserId,
    turnIsOpen,
    isMyTurn,
    inTimerGrace,
    amount,
    setAmount,
    turnStatusMessage,
    onTake,
    onBid,
    onPass,
  } = props;

  return (
    <div className="space-y-4">
      {round && auctionPhaseActive && (
        <div
          key={round.roundId}
          className="bg-surface-card rounded-xl border border-white/10 p-4 animate-fade-in"
        >
          <div className="text-center mb-4">
            <PositionBadgeRow
              primary={round.position}
              eligiblePositions={round.eligiblePositions}
              colors={POSITION_COLORS}
            />
            <h2 className="text-2xl font-extrabold text-white mt-2">{round.cardName}</h2>
            <CardClubLabel clubName={round.cardClubName} className="text-xs text-slate-400 mt-0.5 block" />
            <p className="text-4xl font-black text-card-gold mt-1">{round.cardRating ?? "?"}</p>
            <p className="text-xs text-slate-400 mt-1">Overall</p>
            {round.pace != null && (
              <PlayerAttributeBars
                pace={round.pace}
                shooting={round.shooting ?? 0}
                passing={round.passing ?? 0}
                defending={round.defending ?? 0}
                physical={round.physical ?? 0}
              />
            )}
          </div>

          <div className="mb-4">
            <div className="flex justify-between text-xs text-slate-400 mb-1">
              <span>Time Left</span>
              <span
                className={`font-bold transition-colors duration-300 ${
                  timeExtended
                    ? "text-accent-green"
                    : secondsLeft <= 0
                      ? "text-amber-400 animate-pulse"
                      : secondsLeft <= 5
                        ? "text-red-400"
                        : "text-white"
                }`}
              >
                {timeExtended ? `+${secondsLeft}s Extended!` : secondsLeft <= 0 ? "Time's up!" : `${secondsLeft}s`}
              </span>
            </div>
            <div className="w-full h-2 bg-surface-elevated rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-300 ${
                  timeExtended ? "bg-accent-green" : secondsLeft <= 0 ? "bg-amber-500" : timerColor
                }`}
                style={{ width: `${secondsLeft <= 0 ? 100 : timerPercent}%` }}
              />
            </div>
          </div>

          {timeExtended && (
            <div className="bg-green-900/30 border border-green-500/30 rounded-lg px-3 py-2 mb-4 text-center animate-pulse">
              <p className="text-accent-green text-sm font-medium">Bid extended the round! Keep bidding!</p>
            </div>
          )}

          <div className="bg-surface-elevated rounded-lg p-3 mb-4 text-center transition-all duration-300">
            <p className="text-xs text-slate-400">Highest Bid</p>
            <p key={round.highestBidCents} className="text-2xl font-bold text-white animate-bid-pop">
              {round.highestBidCents > 0 ? `${round.highestBidCents} cr` : "No bids"}
            </p>
            <p className="text-xs text-slate-400 mt-1 transition-opacity duration-300">
              {ROUND_STATUS_LABELS[round.status] ?? round.status}
            </p>
          </div>

          {roundIsOpen && (
            <div
              className={`relative space-y-3 rounded-lg transition-opacity ${
                controlsLocked ? "pointer-events-none opacity-45" : ""
              }`}
            >
              {controlsLocked && (
                <div className="absolute inset-0 z-10 flex items-center justify-center rounded-lg bg-black/25">
                  <p className="text-amber-400 text-sm font-semibold animate-pulse px-3 py-1 rounded bg-black/40">
                    {loading ? "Submitting…" : "Settling round…"}
                  </p>
                </div>
              )}
              {showNoSlotBanner && (
                <div className="bg-slate-800/80 border border-slate-500/30 rounded-lg px-3 py-2 text-center">
                  <p className="text-slate-300 text-sm font-medium">
                    No open slot for this card in your formation
                  </p>
                </div>
              )}
              {showTakePanel ? (
                <>
                  <div className="bg-amber-900/20 border border-amber-500/30 rounded-lg px-3 py-2 text-center">
                    <p className="text-amber-400 text-sm font-medium">Opponent can't bid on this card</p>
                  </div>
                  <button
                    onClick={onTake}
                    disabled={controlsLocked || minimumBid > budget}
                    className="w-full bg-accent-green hover:bg-green-600 disabled:opacity-40 text-white font-bold py-3 rounded-lg transition-colors text-lg"
                  >
                    Take for {minimumBid} cr
                  </button>
                  <button
                    onClick={onPass}
                    disabled={controlsLocked}
                    className="w-full bg-red-900/30 border border-red-500/30 text-red-400 hover:bg-red-900/50 disabled:opacity-40 py-2 rounded-lg transition-colors text-sm font-medium"
                  >
                    Pass
                  </button>
                </>
              ) : iAmHighestBidder && opponentHasBid && canPass && !couldBid ? (
                <button
                  onClick={onPass}
                  disabled={controlsLocked}
                  className="w-full bg-slate-700/50 border border-slate-500/30 text-slate-300 hover:bg-slate-700/70 disabled:opacity-40 py-2 rounded-lg transition-colors text-sm font-medium"
                >
                  End Turn
                </button>
              ) : couldBid ? (
                <>
                  <div className="flex gap-2">
                    <input
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                      type="number"
                      min={1}
                      max={budget}
                      placeholder="Bid (cr)"
                      disabled={controlsLocked || mySquadFull || (!canBid && !controlsLocked)}
                      readOnly={controlsLocked}
                      className="flex-1 bg-surface-elevated border border-white/10 rounded-lg px-3 py-2 text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-accent disabled:opacity-40"
                    />
                    <button
                      onClick={() => {
                        const current = Number(amount);
                        const safe = Number.isFinite(current) ? current : Math.max(1, (round?.highestBidCents ?? 0) + 1);
                        setAmount(String(Math.min(safe + 1, budget)));
                      }}
                      disabled={controlsLocked || mySquadFull || (!canBid && !controlsLocked)}
                      className="bg-surface-elevated border border-white/10 text-white px-3 py-2 rounded-lg hover:bg-white/10 disabled:opacity-40 transition-colors"
                    >
                      +1
                    </button>
                    <button
                      onClick={() => {
                        const current = Number(amount);
                        const safe = Number.isFinite(current) ? current : Math.max(1, (round?.highestBidCents ?? 0) + 1);
                        setAmount(String(Math.min(safe + 5, budget)));
                      }}
                      disabled={controlsLocked || mySquadFull || (!canBid && !controlsLocked)}
                      className="bg-surface-elevated border border-white/10 text-white px-3 py-2 rounded-lg hover:bg-white/10 disabled:opacity-40 transition-colors"
                    >
                      +5
                    </button>
                  </div>
                  <button
                    onClick={onBid}
                    disabled={controlsLocked || mySquadFull || (!canBid && !controlsLocked)}
                    className="w-full bg-accent hover:bg-blue-600 disabled:opacity-40 text-white font-bold py-3 rounded-lg transition-colors"
                  >
                    Place Bid
                  </button>
                  {myUserId && round.highestBidderUserId === myUserId ? (
                    <button
                      onClick={onPass}
                      disabled={controlsLocked || (!canBid && !controlsLocked)}
                      className="w-full bg-slate-700/50 border border-slate-500/30 text-slate-300 hover:bg-slate-700/70 disabled:opacity-40 py-2 rounded-lg transition-colors text-sm font-medium"
                    >
                      End Turn
                    </button>
                  ) : (
                    <button
                      onClick={onPass}
                      disabled={controlsLocked || (!canBid && !controlsLocked)}
                      className="w-full bg-red-900/30 border border-red-500/30 text-red-400 hover:bg-red-900/50 disabled:opacity-40 py-2 rounded-lg transition-colors text-sm font-medium"
                    >
                      Pass
                    </button>
                  )}
                </>
              ) : canPass ? (
                <button
                  onClick={onPass}
                  disabled={controlsLocked}
                  className="w-full bg-red-900/30 border border-red-500/30 text-red-400 hover:bg-red-900/50 disabled:opacity-40 py-2 rounded-lg transition-colors text-sm font-medium"
                >
                  Pass
                </button>
              ) : null}
            </div>
          )}

          <div
            className={`mt-3 text-center text-sm font-medium py-2 rounded-lg transition-colors duration-300 ${
              mySquadFull
                ? "bg-accent-green/10 text-accent-green"
                : controlsLocked
                  ? "bg-amber-500/10 text-amber-400"
                  : inTimerGrace
                    ? "bg-amber-500/10 text-amber-400"
                    : turnIsOpen
                      ? "bg-amber-500/10 text-amber-400"
                      : isMyTurn
                        ? "bg-accent/10 text-accent"
                        : "bg-surface-elevated text-slate-400"
            }`}
          >
            {turnStatusMessage()}
          </div>
        </div>
      )}

      {auctionPhaseActive && !round && (
        <div className="bg-surface-card rounded-xl border border-white/10 p-6 text-center">
          <p className="text-slate-400 animate-pulse">Starting next round...</p>
        </div>
      )}

      {error && (
        <div className="bg-red-900/30 border border-red-500/30 text-red-400 px-4 py-2 rounded-lg text-sm">
          {error}
        </div>
      )}
    </div>
  );
}
