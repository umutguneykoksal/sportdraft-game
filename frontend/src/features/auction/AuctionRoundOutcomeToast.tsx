import type { RoundOutcomeNotice } from "./buildRoundOutcomeNotice";

type Props = {
  notice: RoundOutcomeNotice | null;
};

export function AuctionRoundOutcomeToast({ notice }: Props) {
  if (!notice) {
    return null;
  }

  const ratingSuffix =
    notice.cardRating != null && notice.cardRating > 0 ? ` · ${notice.cardRating} OVR` : "";

  if (notice.kind === "sold") {
    return (
      <div
        className="fixed top-6 left-1/2 z-50 w-[min(100%,22rem)] -translate-x-1/2 px-4 pointer-events-none"
        role="status"
        aria-live="polite"
      >
        <div
          className={`rounded-xl border px-4 py-3 shadow-lg backdrop-blur-sm animate-fade-in ${
            notice.isMine
              ? "bg-accent-green/20 border-accent-green/50"
              : "bg-amber-900/30 border-amber-500/40"
          }`}
        >
          <p className="text-xs uppercase tracking-wide text-slate-300 mb-1">Card won</p>
          <p className="text-white font-bold text-base leading-snug">
            <span className={notice.isMine ? "text-accent-green" : "text-amber-300"}>
              {notice.winnerLabel}
            </span>{" "}
            signed{" "}
            <span className="text-card-gold">{notice.cardName}</span>
            {ratingSuffix}
          </p>
          {notice.amountCents != null && notice.amountCents > 0 && (
            <p className="text-sm text-slate-300 mt-1">for {notice.amountCents} cr</p>
          )}
        </div>
      </div>
    );
  }

  return (
    <div
      className="fixed top-6 left-1/2 z-50 w-[min(100%,22rem)] -translate-x-1/2 px-4 pointer-events-none"
      role="status"
      aria-live="polite"
    >
      <div className="rounded-xl border border-slate-500/40 bg-surface-card/95 px-4 py-3 shadow-lg backdrop-blur-sm animate-fade-in">
        <p className="text-xs uppercase tracking-wide text-slate-400 mb-1">Round ended</p>
        <p className="text-white font-semibold text-base">
          No sale — <span className="text-slate-200">{notice.cardName}</span>
          {ratingSuffix}
        </p>
        {notice.detail && (
          <p className="text-sm text-slate-400 mt-1 capitalize">{notice.detail}</p>
        )}
      </div>
    </div>
  );
}
