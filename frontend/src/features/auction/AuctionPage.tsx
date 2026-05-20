import { useParams } from "react-router-dom";
import { FORMATION_LABELS } from "../../shared/formations";
import { openEligibleHighlights } from "../../shared/positions";
import { AuctionBidPanel } from "./AuctionBidPanel";
import { AuctionRosterPanel } from "./AuctionRosterPanel";
import { useAuctionPageState } from "./hooks/useAuctionPageState";
import { AuctionRoundOutcomeToast } from "./AuctionRoundOutcomeToast";

export function AuctionPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const state = useAuctionPageState(sessionId);

  if (!sessionId) {
    return <div className="p-8 text-white">Session ID missing</div>;
  }

  const pitchHighlights = openEligibleHighlights(
    state.myFormation,
    state.myTeam,
    state.round?.eligiblePositions
  );

  return (
    <div className="min-h-screen bg-surface">
      <AuctionRoundOutcomeToast notice={state.roundOutcomeNotice} />
      <div className="bg-surface-card border-b border-white/10 px-4 py-3">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2 sm:gap-4">
            <h1 className="text-lg sm:text-xl font-bold text-white">Auction Draft</h1>
            <span className="text-xs bg-surface-elevated text-slate-400 px-2 py-1 rounded">
              {FORMATION_LABELS[state.myFormation]}
            </span>
          </div>
          <div className="flex items-center gap-3">
            <div className="text-right">
              <p className="text-xs text-slate-400">Budget</p>
              <p className="text-base sm:text-lg font-bold text-white">
                {state.budget} <span className="text-xs text-slate-400">cr</span>
              </p>
            </div>
            <div className="w-20 sm:w-32 h-2 bg-surface-elevated rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-300 ${
                  state.budgetPercent > 30
                    ? "bg-accent-green"
                    : state.budgetPercent > 10
                      ? "bg-amber-500"
                      : "bg-red-500"
                }`}
                style={{ width: `${state.budgetPercent}%` }}
              />
            </div>
            {!state.isConnected && (
              <span className="text-amber-400 text-xs animate-pulse">Reconnecting...</span>
            )}
          </div>
        </div>
      </div>

      {state.sessionSnap?.status === "MATCH_START_PENDING" && (
        <div className="bg-accent-green/10 border-b border-accent-green/30 px-4 py-4 text-center">
          <p className="text-lg font-bold text-accent-green animate-pulse">
            All squads complete! Starting match...
          </p>
        </div>
      )}

      {(state.sessionSnap?.status === "AWAITING_RESULT" || state.sessionSnap?.status === "FINALIZING") && (
        <div className="bg-surface-card border-b border-white/10 px-4 py-6 text-center">
          <div className="animate-pulse">
            <p className="text-xl font-bold text-white">Simulating Match...</p>
            <p className="text-slate-400 text-sm mt-1">Results coming shortly</p>
          </div>
        </div>
      )}

      <div className="max-w-7xl mx-auto px-4 py-6">
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px_1fr] gap-6">
          <AuctionRosterPanel
            title="My Team"
            formation={state.myFormation}
            cards={state.myTeam}
            teamColor="#3b82f6"
            teamBorderColor="#60a5fa"
            highlightPositions={pitchHighlights}
          />

          <AuctionBidPanel
            round={state.round}
            auctionPhaseActive={state.auctionPhaseActive}
            error={state.error}
            secondsLeft={state.secondsLeft}
            timeExtended={state.timeExtended}
            timerPercent={state.timerPercent}
            timerColor={state.timerColor}
            minimumBid={state.minimumBid}
            budget={state.budget}
            roundIsOpen={state.roundIsOpen}
            controlsLocked={state.controlsLocked}
            loading={state.loading}
            canBid={state.canBid}
            couldBid={state.couldBid}
            canPass={state.canPass}
            canFillCard={state.canFillCard}
            showTakePanel={state.showTakePanel}
            showNoSlotBanner={state.showNoSlotBanner}
            waitingOnOpponent={state.waitingOnOpponent}
            iAmHighestBidder={state.iAmHighestBidder}
            opponentHasBid={state.opponentHasBid}
            mySquadFull={state.mySquadFull}
            myUserId={state.myUserId}
            turnIsOpen={state.turnIsOpen}
            isMyTurn={state.isMyTurn}
            inTimerGrace={state.inTimerGrace}
            amount={state.amount}
            setAmount={state.setAmount}
            turnStatusMessage={state.turnStatusMessage}
            onTake={state.handleTake}
            onBid={state.handleBid}
            onPass={state.handlePassRound}
          />

          <AuctionRosterPanel
            title="Opponent"
            formation={state.opponentFormation}
            cards={state.opponentTeam}
            away
            teamColor="#ef4444"
            teamBorderColor="#f87171"
          />
        </div>
      </div>
    </div>
  );
}
