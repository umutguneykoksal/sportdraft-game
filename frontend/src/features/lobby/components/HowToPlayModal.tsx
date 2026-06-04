import {
  FORMATION_FIRST_READY_COUNTDOWN_SECONDS,
  LOBBY_PAIRING_COUNTDOWN_SECONDS,
} from "../../../shared/lobbyTiming";
import { LobbyModalShell } from "./LobbyModalShell";

interface HowToPlaySectionProps {
  title: string;
  children: React.ReactNode;
}

function HowToPlaySection({ title, children }: HowToPlaySectionProps) {
  return (
    <section className="rounded-lg border border-white/10 bg-surface-elevated/40 p-3">
      <h3 className="mb-1.5 text-xs font-semibold uppercase tracking-wide text-accent">{title}</h3>
      <div className="space-y-1 text-xs leading-relaxed text-slate-300">{children}</div>
    </section>
  );
}

interface HowToPlayModalProps {
  open: boolean;
  onClose: () => void;
}

export function HowToPlayModal({ open, onClose }: HowToPlayModalProps) {
  return (
    <LobbyModalShell open={open} title="How to play" onClose={onClose} testId="how-to-play-modal">
      <div className="min-h-0 flex-1 space-y-2 overflow-y-auto px-4 py-3">
        <HowToPlaySection title="Lobby">
          <p>Create or join a room, pick a league and minimum OVR, then choose your formation.</p>
          <p>
            When one player taps <strong className="text-white">Start Auction</strong>, a{" "}
            <strong className="text-white">{FORMATION_FIRST_READY_COUNTDOWN_SECONDS}-second</strong>{" "}
            timer begins. If both confirm before it ends, the auction starts immediately.
          </p>
          <p>
            After pairing, a{" "}
            <strong className="text-white">{LOBBY_PAIRING_COUNTDOWN_SECONDS}-second</strong> countdown
            leads into formation pick.
          </p>
        </HowToPlaySection>

        <HowToPlaySection title="Auction">
          <p>Each player starts with <strong className="text-white">100 credits</strong>.</p>
          <p>Cards are auctioned one at a time. Fill only slots your formation allows.</p>
          <p>
            Early in a round, turns are exclusive — then bidding opens to everyone with a slot in the final{" "}
            <strong className="text-white">15 seconds</strong>.
          </p>
          <p>Pass if you cannot use a card. Anti-snipe may extend the clock on late bids.</p>
        </HowToPlaySection>

        <HowToPlaySection title="Game modes">
          <p>
            <strong className="text-white">Duel</strong> — head-to-head draft and one match.
          </p>
          <p>
            <strong className="text-white">Tournament</strong> — shared draft, bracket semi-finals, then a final.
          </p>
          <p>
            <strong className="text-white">League</strong> — shared draft, round-robin matchdays, table, optional playoff.
          </p>
        </HowToPlaySection>
      </div>
      <footer className="shrink-0 border-t border-white/10 px-4 py-3">
        <button
          type="button"
          onClick={onClose}
          className="w-full rounded-md bg-accent py-2 text-sm font-bold text-white hover:bg-blue-600"
        >
          Got it
        </button>
      </footer>
    </LobbyModalShell>
  );
}
