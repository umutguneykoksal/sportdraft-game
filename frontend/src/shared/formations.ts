import type { Formation, Position } from "./api/contracts";

export interface FormationSlots {
  df: number;
  cm: number;
  fw: number;
}

export const FORMATION_SLOTS: Record<Formation, FormationSlots> = {
  FOUR_FOUR_TWO: { df: 4, cm: 4, fw: 2 },
  FOUR_THREE_THREE: { df: 4, cm: 3, fw: 3 },
  THREE_FIVE_TWO: { df: 3, cm: 5, fw: 2 },
};

export const FORMATION_LABELS: Record<Formation, string> = {
  FOUR_FOUR_TWO: "4-4-2",
  FOUR_THREE_THREE: "4-3-3",
  THREE_FIVE_TWO: "3-5-2",
};

export const ALL_FORMATIONS: Formation[] = [
  "FOUR_FOUR_TWO",
  "FOUR_THREE_THREE",
  "THREE_FIVE_TWO",
];

/** Maps backend Position to react-soccer-lineup squad field */
export function positionToSquadKey(pos: Position): "gk" | "df" | "cm" | "fw" {
  switch (pos) {
    case "GK":
      return "gk";
    case "DEF":
      return "df";
    case "MID":
      return "cm";
    case "FWD":
      return "fw";
  }
}

export function positionLabel(pos: Position): string {
  switch (pos) {
    case "GK":
      return "GK";
    case "DEF":
      return "DEF";
    case "MID":
      return "MID";
    case "FWD":
      return "FWD";
  }
}
