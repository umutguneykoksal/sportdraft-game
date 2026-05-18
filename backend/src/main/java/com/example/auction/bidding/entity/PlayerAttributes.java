package com.example.auction.bidding.entity;

/**
 * Five FM-style attributes (1–99). {@code rating} on {@link Card} remains the displayed overall.
 */
public record PlayerAttributes(int pace, int shooting, int passing, int defending, int physical) {

    public PlayerAttributes {
        pace = clamp(pace);
        shooting = clamp(shooting);
        passing = clamp(passing);
        defending = clamp(defending);
        physical = clamp(physical);
    }

    public static PlayerAttributes fromOverall(Position position, int overall) {
        return switch (position) {
            case GK -> new PlayerAttributes(
                overall - 15, overall - 35, overall - 5, overall + 8, overall + 2
            );
            case DEF -> new PlayerAttributes(
                overall - 5, overall - 14, overall - 2, overall + 5, overall + 4
            );
            case CDM -> new PlayerAttributes(
                overall - 3, overall - 10, overall, overall + 4, overall + 3
            );
            case CM -> new PlayerAttributes(
                overall, overall - 5, overall + 4, overall, overall + 1
            );
            case CAM -> new PlayerAttributes(
                overall + 2, overall + 2, overall + 5, overall - 8, overall
            );
            case FWD -> new PlayerAttributes(
                overall + 3, overall + 5, overall - 3, overall - 12, overall + 2
            );
        };
    }

    private static int clamp(int value) {
        return Math.max(1, Math.min(99, value));
    }
}
