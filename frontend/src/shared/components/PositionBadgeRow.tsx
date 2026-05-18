import { orderedEligiblePositions } from "../positions";

const DEFAULT_COLORS: Record<string, string> = {
  GK: "bg-yellow-500",
  DEF: "bg-blue-500",
  CDM: "bg-indigo-500",
  CM: "bg-green-500",
  CAM: "bg-orange-500",
  FWD: "bg-red-500",
};

interface PositionBadgeRowProps {
  primary: string;
  eligiblePositions?: string[];
  colors?: Record<string, string>;
  size?: "sm" | "md";
}

export function PositionBadgeRow({
  primary,
  eligiblePositions,
  colors = DEFAULT_COLORS,
  size = "md",
}: PositionBadgeRowProps) {
  const positions = orderedEligiblePositions(primary, eligiblePositions);
  const pad = size === "sm" ? "px-1.5 py-0.5 text-xs" : "px-2 py-1 text-xs";

  return (
    <span className="inline-flex items-center gap-1 justify-center flex-wrap">
      {positions.map((pos, index) => {
        const isPrimary = index === 0;
        const colorClass = colors[pos] ?? "bg-gray-500";
        if (isPrimary) {
          return (
            <span
              key={pos}
              className={`${colorClass} text-white ${pad} rounded-full font-bold`}
              title="Main position"
            >
              {pos}
            </span>
          );
        }
        return (
          <span
            key={pos}
            className={`${pad} rounded-full font-semibold text-slate-200 bg-slate-800 ring-1 ring-inset ring-white/25`}
            title="Also plays"
          >
            {pos}
          </span>
        );
      })}
    </span>
  );
}
