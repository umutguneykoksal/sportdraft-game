interface PlayerAttributeBarsProps {
  pace: number;
  shooting: number;
  passing: number;
  defending: number;
  physical: number;
}

const LABELS = [
  { key: "pace", label: "PAC" },
  { key: "shooting", label: "SHO" },
  { key: "passing", label: "PAS" },
  { key: "defending", label: "DEF" },
  { key: "physical", label: "PHY" },
] as const;

export function PlayerAttributeBars(props: PlayerAttributeBarsProps) {
  const values: Record<string, number> = {
    pace: props.pace,
    shooting: props.shooting,
    passing: props.passing,
    defending: props.defending,
    physical: props.physical,
  };

  return (
    <div className="grid grid-cols-5 gap-1 mt-3 max-w-xs mx-auto">
      {LABELS.map(({ key, label }) => (
        <div key={key} className="text-center">
          <div className="text-[10px] text-slate-500 font-medium">{label}</div>
          <div className="text-sm font-bold text-slate-200">{values[key]}</div>
        </div>
      ))}
    </div>
  );
}
