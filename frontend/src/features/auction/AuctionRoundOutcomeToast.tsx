import type { RoundOutcomeNotice } from "./buildRoundOutcomeNotice";

type Props = {
  notice: RoundOutcomeNotice | null;
};

function ratingSuffix(cardRating?: number): string {
  return cardRating != null && cardRating > 0 ? ` · ${cardRating} OVR` : "";
}

export function AuctionRoundOutcomeToast({ notice }: Props) {
  if (!notice) {
    return null;
  }

  if (notice.kind === "sold") {
    return (
      <div
        className={`border-b animate-fade-in ${
          notice.isMine
            ? "border-accent-green/30 bg-accent-green/10"
            : "border-amber-500/30 bg-amber-500/10"
        }`}
        role="status"
        aria-live="polite"
        data-testid="auction-round-outcome"
      >
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-center gap-x-2 gap-y-1 px-4 py-2.5 text-center text-sm">
          <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">Sold</span>
          <p className="font-semibold text-white">
            <span className={notice.isMine ? "text-accent-green" : "text-amber-300"}>
              {notice.winnerLabel}
            </span>
            <span className="text-slate-400"> signed </span>
            <span className="text-card-gold">{notice.cardName}</span>
            {ratingSuffix(notice.cardRating)}
            {notice.amountCents != null && notice.amountCents > 0 && (
              <span className="text-slate-300"> for {notice.amountCents} cr</span>
            )}
          </p>
        </div>
      </div>
    );
  }

  const showDetail =
    notice.detail != null && notice.detail.toLowerCase() !== "no one bought this card";

  return (
    <div
      className="animate-fade-in border-b border-white/10 bg-surface-elevated/40"
      role="status"
      aria-live="polite"
      data-testid="auction-round-outcome"
    >
      <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-center gap-x-2 gap-y-1 px-4 py-2.5 text-center text-sm">
        <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-500">Unsold</span>
        <p className="font-semibold text-white">
          <span className="text-slate-200">{notice.cardName}</span>
          {ratingSuffix(notice.cardRating)}
        </p>
        {showDetail && (
          <p className="w-full text-xs text-slate-400 capitalize">{notice.detail}</p>
        )}
      </div>
    </div>
  );
}
