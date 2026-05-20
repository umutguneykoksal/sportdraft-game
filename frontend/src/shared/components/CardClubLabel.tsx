type Props = {
  clubName?: string | null;
  className?: string;
};

/** Small secondary label for a footballer's real-world club. */
export function CardClubLabel({ clubName, className = "text-slate-500 text-xs" }: Props) {
  const label = clubName?.trim();
  if (!label) return null;
  return <span className={className}>{label}</span>;
}
