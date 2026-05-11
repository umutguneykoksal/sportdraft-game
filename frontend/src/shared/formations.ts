import type { Formation, Position } from "./api/contracts";

export interface FormationSlots {
  df: number;
  cm: number;
  fw: number;
}

export const FORMATION_SLOTS: Record<Formation, FormationSlots> = {
  FOUR_FOUR_TWO:       { df: 4, cm: 4, fw: 2 },
  FOUR_THREE_THREE:    { df: 4, cm: 3, fw: 3 },
  THREE_FIVE_TWO:      { df: 3, cm: 5, fw: 2 },
  FOUR_TWO_THREE_ONE:  { df: 4, cm: 5, fw: 1 },
  THREE_FOUR_THREE:    { df: 3, cm: 4, fw: 3 },
  FIVE_THREE_TWO:      { df: 5, cm: 3, fw: 2 },
  FOUR_ONE_FOUR_ONE:   { df: 4, cm: 5, fw: 1 },
  FOUR_FIVE_ONE:       { df: 4, cm: 5, fw: 1 },
  FOUR_THREE_TWO_ONE:  { df: 4, cm: 5, fw: 1 },
  FOUR_FOUR_ONE_ONE:   { df: 4, cm: 4, fw: 2 },
};

export const FORMATION_LABELS: Record<Formation, string> = {
  FOUR_FOUR_TWO:       "4-4-2",
  FOUR_THREE_THREE:    "4-3-3",
  THREE_FIVE_TWO:      "3-5-2",
  FOUR_TWO_THREE_ONE:  "4-2-3-1",
  THREE_FOUR_THREE:    "3-4-3",
  FIVE_THREE_TWO:      "5-3-2",
  FOUR_ONE_FOUR_ONE:   "4-1-4-1",
  FOUR_FIVE_ONE:       "4-5-1",
  FOUR_THREE_TWO_ONE:  "4-3-2-1",
  FOUR_FOUR_ONE_ONE:   "4-4-1-1",
};

export const ALL_FORMATIONS: Formation[] = [
  "FOUR_FOUR_TWO",
  "FOUR_THREE_THREE",
  "THREE_FIVE_TWO",
  "FOUR_TWO_THREE_ONE",
  "THREE_FOUR_THREE",
  "FIVE_THREE_TWO",
  "FOUR_ONE_FOUR_ONE",
  "FOUR_FIVE_ONE",
  "FOUR_THREE_TWO_ONE",
  "FOUR_FOUR_ONE_ONE",
];

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
