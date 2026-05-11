import type { Formation, Position } from "./api/contracts";

export interface FormationSlots {
  def: number;
  cdm: number;
  cm: number;
  cam: number;
  fwd: number;
}

export const FORMATION_SLOTS: Record<Formation, FormationSlots> = {
  FOUR_FOUR_TWO:       { def: 4, cdm: 0, cm: 4, cam: 0, fwd: 2 },
  FOUR_THREE_THREE:    { def: 4, cdm: 0, cm: 3, cam: 0, fwd: 3 },
  THREE_FIVE_TWO:      { def: 3, cdm: 0, cm: 5, cam: 0, fwd: 2 },
  FOUR_TWO_THREE_ONE:  { def: 4, cdm: 2, cm: 0, cam: 3, fwd: 1 },
  THREE_FOUR_THREE:    { def: 3, cdm: 0, cm: 4, cam: 0, fwd: 3 },
  FIVE_THREE_TWO:      { def: 5, cdm: 0, cm: 3, cam: 0, fwd: 2 },
  FOUR_ONE_FOUR_ONE:   { def: 4, cdm: 1, cm: 4, cam: 0, fwd: 1 },
  FOUR_FIVE_ONE:       { def: 4, cdm: 0, cm: 5, cam: 0, fwd: 1 },
  FOUR_THREE_TWO_ONE:  { def: 4, cdm: 0, cm: 3, cam: 2, fwd: 1 },
  FOUR_FOUR_ONE_ONE:   { def: 4, cdm: 0, cm: 4, cam: 1, fwd: 1 },
};

export interface FormationDot {
  x: number;
  y: number;
  pos: Position;
}

export const FORMATION_DOTS: Record<Formation, FormationDot[]> = {
  FOUR_FOUR_TWO: [
    { x: 50, y: 92, pos: "GK" },
    { x: 20, y: 70, pos: "DEF" }, { x: 40, y: 70, pos: "DEF" }, { x: 60, y: 70, pos: "DEF" }, { x: 80, y: 70, pos: "DEF" },
    { x: 20, y: 42, pos: "CM" }, { x: 40, y: 42, pos: "CM" }, { x: 60, y: 42, pos: "CM" }, { x: 80, y: 42, pos: "CM" },
    { x: 35, y: 16, pos: "FWD" }, { x: 65, y: 16, pos: "FWD" },
  ],
  FOUR_THREE_THREE: [
    { x: 50, y: 92, pos: "GK" },
    { x: 20, y: 70, pos: "DEF" }, { x: 40, y: 70, pos: "DEF" }, { x: 60, y: 70, pos: "DEF" }, { x: 80, y: 70, pos: "DEF" },
    { x: 30, y: 42, pos: "CM" }, { x: 50, y: 42, pos: "CM" }, { x: 70, y: 42, pos: "CM" },
    { x: 25, y: 16, pos: "FWD" }, { x: 50, y: 16, pos: "FWD" }, { x: 75, y: 16, pos: "FWD" },
  ],
  THREE_FIVE_TWO: [
    { x: 50, y: 92, pos: "GK" },
    { x: 25, y: 70, pos: "DEF" }, { x: 50, y: 70, pos: "DEF" }, { x: 75, y: 70, pos: "DEF" },
    { x: 15, y: 42, pos: "CM" }, { x: 32, y: 42, pos: "CM" }, { x: 50, y: 42, pos: "CM" }, { x: 68, y: 42, pos: "CM" }, { x: 85, y: 42, pos: "CM" },
    { x: 35, y: 16, pos: "FWD" }, { x: 65, y: 16, pos: "FWD" },
  ],
  FOUR_TWO_THREE_ONE: [
    { x: 50, y: 92, pos: "GK" },
    { x: 20, y: 72, pos: "DEF" }, { x: 40, y: 72, pos: "DEF" }, { x: 60, y: 72, pos: "DEF" }, { x: 80, y: 72, pos: "DEF" },
    { x: 35, y: 54, pos: "CDM" }, { x: 65, y: 54, pos: "CDM" },
    { x: 25, y: 34, pos: "CAM" }, { x: 50, y: 34, pos: "CAM" }, { x: 75, y: 34, pos: "CAM" },
    { x: 50, y: 14, pos: "FWD" },
  ],
  THREE_FOUR_THREE: [
    { x: 50, y: 92, pos: "GK" },
    { x: 25, y: 70, pos: "DEF" }, { x: 50, y: 70, pos: "DEF" }, { x: 75, y: 70, pos: "DEF" },
    { x: 20, y: 42, pos: "CM" }, { x: 40, y: 42, pos: "CM" }, { x: 60, y: 42, pos: "CM" }, { x: 80, y: 42, pos: "CM" },
    { x: 25, y: 16, pos: "FWD" }, { x: 50, y: 16, pos: "FWD" }, { x: 75, y: 16, pos: "FWD" },
  ],
  FIVE_THREE_TWO: [
    { x: 50, y: 92, pos: "GK" },
    { x: 12, y: 70, pos: "DEF" }, { x: 30, y: 70, pos: "DEF" }, { x: 50, y: 70, pos: "DEF" }, { x: 70, y: 70, pos: "DEF" }, { x: 88, y: 70, pos: "DEF" },
    { x: 30, y: 42, pos: "CM" }, { x: 50, y: 42, pos: "CM" }, { x: 70, y: 42, pos: "CM" },
    { x: 35, y: 16, pos: "FWD" }, { x: 65, y: 16, pos: "FWD" },
  ],
  FOUR_ONE_FOUR_ONE: [
    { x: 50, y: 92, pos: "GK" },
    { x: 20, y: 72, pos: "DEF" }, { x: 40, y: 72, pos: "DEF" }, { x: 60, y: 72, pos: "DEF" }, { x: 80, y: 72, pos: "DEF" },
    { x: 50, y: 54, pos: "CDM" },
    { x: 15, y: 34, pos: "CM" }, { x: 38, y: 34, pos: "CM" }, { x: 62, y: 34, pos: "CM" }, { x: 85, y: 34, pos: "CM" },
    { x: 50, y: 14, pos: "FWD" },
  ],
  FOUR_FIVE_ONE: [
    { x: 50, y: 92, pos: "GK" },
    { x: 20, y: 70, pos: "DEF" }, { x: 40, y: 70, pos: "DEF" }, { x: 60, y: 70, pos: "DEF" }, { x: 80, y: 70, pos: "DEF" },
    { x: 15, y: 42, pos: "CM" }, { x: 32, y: 42, pos: "CM" }, { x: 50, y: 42, pos: "CM" }, { x: 68, y: 42, pos: "CM" }, { x: 85, y: 42, pos: "CM" },
    { x: 50, y: 16, pos: "FWD" },
  ],
  FOUR_THREE_TWO_ONE: [
    { x: 50, y: 92, pos: "GK" },
    { x: 20, y: 72, pos: "DEF" }, { x: 40, y: 72, pos: "DEF" }, { x: 60, y: 72, pos: "DEF" }, { x: 80, y: 72, pos: "DEF" },
    { x: 30, y: 52, pos: "CM" }, { x: 50, y: 52, pos: "CM" }, { x: 70, y: 52, pos: "CM" },
    { x: 35, y: 32, pos: "CAM" }, { x: 65, y: 32, pos: "CAM" },
    { x: 50, y: 14, pos: "FWD" },
  ],
  FOUR_FOUR_ONE_ONE: [
    { x: 50, y: 92, pos: "GK" },
    { x: 20, y: 72, pos: "DEF" }, { x: 40, y: 72, pos: "DEF" }, { x: 60, y: 72, pos: "DEF" }, { x: 80, y: 72, pos: "DEF" },
    { x: 20, y: 48, pos: "CM" }, { x: 40, y: 48, pos: "CM" }, { x: 60, y: 48, pos: "CM" }, { x: 80, y: 48, pos: "CM" },
    { x: 50, y: 28, pos: "CAM" },
    { x: 50, y: 12, pos: "FWD" },
  ],
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

export function positionLabel(pos: Position): string {
  return pos;
}
