import { useEffect, useRef } from "react";
import { X } from "lucide-react";

interface LobbyModalShellProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: React.ReactNode;
  testId?: string;
}

export function LobbyModalShell({ open, title, onClose, children, testId }: LobbyModalShellProps) {
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [open, onClose]);

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      onClick={onClose}
      data-testid={testId}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="lobby-modal-title"
        className="flex max-h-[min(560px,calc(100dvh-2rem))] w-full max-w-lg flex-col overflow-hidden rounded-2xl border border-white/10 bg-surface-card shadow-2xl shadow-black/40"
        onClick={(e) => e.stopPropagation()}
      >
        <header className="flex shrink-0 items-center justify-between border-b border-white/10 px-4 py-3">
          <h2 id="lobby-modal-title" className="text-sm font-bold text-white sm:text-base">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="rounded-md p-1 text-slate-400 hover:bg-white/10 hover:text-white"
          >
            <X className="h-4 w-4" aria-hidden="true" />
          </button>
        </header>
        {children}
      </div>
    </div>
  );
}
