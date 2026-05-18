package com.example.auction.simulation.control;

import com.example.auction.bidding.entity.PlayerAttributes;
import com.example.auction.bidding.entity.Position;

/**
 * Position-aware attack/defense strength from five attributes.
 */
public final class PlayerStrength {

    private PlayerStrength() {
    }

    public static double attack(Position assigned, PlayerAttributes a) {
        return switch (assigned) {
            case FWD -> 0.40 * a.shooting() + 0.25 * a.pace() + 0.20 * a.passing() + 0.15 * a.physical();
            case CAM -> 0.35 * a.shooting() + 0.30 * a.passing() + 0.20 * a.pace() + 0.15 * a.physical();
            case CM -> 0.30 * a.passing() + 0.25 * a.shooting() + 0.25 * a.pace() + 0.20 * a.defending();
            case CDM -> 0.25 * a.passing() + 0.20 * a.shooting() + 0.25 * a.pace() + 0.30 * a.defending();
            case DEF -> 0.15 * a.passing() + 0.10 * a.shooting() + 0.15 * a.pace();
            case GK -> 0;
        };
    }

    public static double defense(Position assigned, PlayerAttributes a) {
        return switch (assigned) {
            case GK -> 0.55 * a.defending() + 0.30 * a.physical() + 0.15 * a.pace();
            case DEF -> 0.45 * a.defending() + 0.25 * a.physical() + 0.20 * a.pace() + 0.10 * a.passing();
            case CDM -> 0.40 * a.defending() + 0.25 * a.passing() + 0.20 * a.physical() + 0.15 * a.pace();
            case CM -> 0.30 * a.defending() + 0.30 * a.passing() + 0.25 * a.physical() + 0.15 * a.pace();
            case CAM -> 0.25 * a.defending() + 0.30 * a.passing() + 0.25 * a.pace() + 0.20 * a.physical();
            case FWD -> 0.20 * a.defending() + 0.25 * a.pace() + 0.25 * a.physical() + 0.30 * a.passing();
        };
    }
}
