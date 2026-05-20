import { useNavigate, useParams } from "react-router-dom";
import { clearActiveSessionId } from "../../shared/session";

export function LeaguePendingPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-surface flex flex-col items-center justify-center px-4 py-8">
      <div className="w-full max-w-md bg-surface-card rounded-xl border border-white/10 p-8 text-center space-y-4">
        <h1 className="text-2xl font-bold text-white">League mode</h1>
        <p className="text-slate-400 text-sm">
          Your 4-player draft is complete. Round-robin league fixtures and standings are coming in a
          future update.
        </p>
        {sessionId && (
          <p className="text-xs text-slate-500 font-mono break-all">Session {sessionId}</p>
        )}
        <button
          type="button"
          onClick={() => {
            clearActiveSessionId();
            navigate("/lobby");
          }}
          className="w-full bg-accent hover:bg-blue-600 text-white font-semibold py-3 rounded-xl transition-colors"
        >
          Back to lobby
        </button>
      </div>
    </div>
  );
}
