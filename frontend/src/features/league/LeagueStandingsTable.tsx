import type { LeagueStandingRow } from "../../shared/api/contracts";
import { getClientUserId } from "../../shared/api";

export function LeagueStandingsTable({ standings }: { standings: LeagueStandingRow[] }) {
  const myUserId = getClientUserId();

  if (standings.length === 0) {
    return (
      <p className="text-slate-500 text-sm">Standings update as matches finish.</p>
    );
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-slate-500 text-left border-b border-white/10">
            <th className="py-2 pr-2">#</th>
            <th className="py-2 pr-2">Player</th>
            <th className="py-2 px-1 text-center">O</th>
            <th className="py-2 px-1 text-center">W</th>
            <th className="py-2 px-1 text-center">D</th>
            <th className="py-2 px-1 text-center">L</th>
            <th className="py-2 px-1 text-center">GF</th>
            <th className="py-2 px-1 text-center">GA</th>
            <th className="py-2 px-1 text-center">GD</th>
            <th className="py-2 pl-1 text-center font-semibold">Pts</th>
          </tr>
        </thead>
        <tbody>
          {standings.map((row) => {
            const isMe = row.userId === myUserId;
            return (
              <tr
                key={row.userId}
                className={`border-b border-white/5 ${isMe ? "bg-accent/10" : ""}`}
              >
                <td className="py-2 pr-2 text-slate-400">{row.rank}</td>
                <td className="py-2 pr-2 text-white font-medium truncate max-w-[7rem]">
                  {row.nickname}
                  {isMe && <span className="text-accent text-xs ml-1">(you)</span>}
                </td>
                <td className="py-2 px-1 text-center text-slate-300">{row.played}</td>
                <td className="py-2 px-1 text-center text-slate-300">{row.won}</td>
                <td className="py-2 px-1 text-center text-slate-300">{row.drawn}</td>
                <td className="py-2 px-1 text-center text-slate-300">{row.lost}</td>
                <td className="py-2 px-1 text-center text-slate-300">{row.goalsFor}</td>
                <td className="py-2 px-1 text-center text-slate-300">{row.goalsAgainst}</td>
                <td className="py-2 px-1 text-center text-slate-300">{row.goalDifference}</td>
                <td className="py-2 pl-1 text-center text-card-gold font-bold">{row.points}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
