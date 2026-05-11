package com.example.auction.session.entity;

import com.example.auction.bidding.entity.Position;

public enum Formation {
    FOUR_FOUR_TWO       ("4-4-2",     1, 4, 4, 2, 1.0,  1.0),
    FOUR_THREE_THREE    ("4-3-3",     1, 4, 3, 3, 1.05, 1.0),
    THREE_FIVE_TWO      ("3-5-2",     1, 3, 5, 2, 1.03, 0.95),
    FOUR_TWO_THREE_ONE  ("4-2-3-1",   1, 4, 5, 1, 1.02, 1.0),
    THREE_FOUR_THREE    ("3-4-3",     1, 3, 4, 3, 1.06, 0.93),
    FIVE_THREE_TWO      ("5-3-2",     1, 5, 3, 2, 0.97, 1.08),
    FOUR_ONE_FOUR_ONE   ("4-1-4-1",   1, 4, 5, 1, 1.0,  1.02),
    FOUR_FIVE_ONE       ("4-5-1",     1, 4, 5, 1, 0.98, 1.04),
    FOUR_THREE_TWO_ONE  ("4-3-2-1",   1, 4, 5, 1, 1.01, 1.0),
    FOUR_FOUR_ONE_ONE   ("4-4-1-1",   1, 4, 4, 2, 1.0,  1.02);

    private final String label;
    private final int gk;
    private final int def;
    private final int mid;
    private final int fwd;
    private final double attackMod;
    private final double defenseMod;

    Formation(String label, int gk, int def, int mid, int fwd,
              double attackMod, double defenseMod) {
        this.label = label;
        this.gk = gk;
        this.def = def;
        this.mid = mid;
        this.fwd = fwd;
        this.attackMod = attackMod;
        this.defenseMod = defenseMod;
    }

    public String getLabel() {
        return label;
    }

    public int getGk() {
        return gk;
    }

    public int getDef() {
        return def;
    }

    public int getMid() {
        return mid;
    }

    public int getFwd() {
        return fwd;
    }

    public double getAttackMod() {
        return attackMod;
    }

    public double getDefenseMod() {
        return defenseMod;
    }

    public int slotCount(Position position) {
        return switch (position) {
            case GK -> gk;
            case DEF -> def;
            case MID -> mid;
            case FWD -> fwd;
        };
    }
}
