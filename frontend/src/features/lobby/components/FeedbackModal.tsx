import { type FormEvent, useState } from "react";
import { Loader2, MessageSquarePlus } from "lucide-react";
import { toast } from "sonner";
import { submitFeedback, type FeedbackCategory } from "../../../shared/api/feedbackClient";
import { ApiError } from "../../../shared/api/errors";
import { cn } from "../../../shared/utils/cn";
import { LobbyModalShell } from "./LobbyModalShell";

const CATEGORY_OPTIONS: { id: FeedbackCategory; label: string }[] = [
  { id: "BUG", label: "Bug" },
  { id: "IDEA", label: "Idea" },
  { id: "OTHER", label: "Other" },
];

const MIN_MESSAGE_LENGTH = 10;
const MAX_MESSAGE_LENGTH = 2000;

interface FeedbackModalProps {
  open: boolean;
  onClose: () => void;
}

export function FeedbackModal({ open, onClose }: FeedbackModalProps) {
  const [category, setCategory] = useState<FeedbackCategory>("BUG");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const trimmed = message.trim();
  const canSubmit = trimmed.length >= MIN_MESSAGE_LENGTH && !submitting;

  const resetAndClose = () => {
    setCategory("BUG");
    setMessage("");
    setSubmitting(false);
    onClose();
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;

    setSubmitting(true);
    try {
      await submitFeedback({
        category,
        message: trimmed,
        pagePath: window.location.pathname,
        appVersion: process.env.REACT_APP_VERSION,
      });
      toast.success("Thanks — your feedback was sent anonymously.");
      resetAndClose();
    } catch (err) {
      const detail =
        err instanceof ApiError && err.status === 429
          ? "Too many submissions — please try again later."
          : err instanceof Error
            ? err.message
            : "Could not send feedback.";
      toast.error(detail);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <LobbyModalShell open={open} title="Send feedback" onClose={resetAndClose} testId="feedback-modal">
      <form onSubmit={handleSubmit} className="flex min-h-0 flex-1 flex-col">
        <div className="min-h-0 flex-1 space-y-3 overflow-y-auto px-4 py-3">
          <p className="text-xs leading-relaxed text-slate-400">
            Anonymous — we do not attach your nickname or session. Help us improve SportDraft.
          </p>

          <fieldset>
            <legend className="mb-1.5 text-[11px] font-medium text-slate-400">Category</legend>
            <div className="flex gap-2">
              {CATEGORY_OPTIONS.map((option) => (
                <button
                  key={option.id}
                  type="button"
                  data-testid={`feedback-category-${option.id}`}
                  onClick={() => setCategory(option.id)}
                  className={cn(
                    "flex-1 rounded-md border py-1.5 text-xs font-semibold transition-colors",
                    category === option.id
                      ? "border-accent bg-accent/15 text-white"
                      : "border-white/10 bg-surface-elevated text-slate-300 hover:border-white/25"
                  )}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </fieldset>

          <div>
            <label htmlFor="feedback-message" className="mb-1.5 block text-[11px] font-medium text-slate-400">
              Message
            </label>
            <textarea
              id="feedback-message"
              data-testid="feedback-message"
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              maxLength={MAX_MESSAGE_LENGTH}
              rows={5}
              placeholder="What happened? What would you like to see?"
              className="w-full resize-none rounded-md border border-white/10 bg-surface-elevated px-3 py-2 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-accent"
            />
            <div className="mt-1 flex justify-between text-[10px] text-slate-500">
              <span>{trimmed.length < MIN_MESSAGE_LENGTH ? `Min ${MIN_MESSAGE_LENGTH} characters` : " "}</span>
              <span className="tabular-nums">
                {message.length}/{MAX_MESSAGE_LENGTH}
              </span>
            </div>
          </div>
        </div>

        <footer className="shrink-0 border-t border-white/10 px-4 py-3">
          <button
            type="submit"
            data-testid="feedback-submit"
            disabled={!canSubmit}
            className="inline-flex w-full items-center justify-center gap-1.5 rounded-md bg-accent py-2 text-sm font-bold text-white hover:bg-blue-600 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                Sending...
              </>
            ) : (
              <>
                <MessageSquarePlus className="h-4 w-4" aria-hidden="true" />
                Send anonymously
              </>
            )}
          </button>
        </footer>
      </form>
    </LobbyModalShell>
  );
}
