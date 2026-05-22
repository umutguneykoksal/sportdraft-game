package dev.sportdraft.bidding.control;

/** Shared auction timing and bid rules (package-private). */
final class AuctionTiming {

    static final long MIN_INCREMENT = 1L;
    static final long STARTING_BID = 1L;
    static final int ROSTER_SIZE = 11;
    static final long MATCH_START_COUNTDOWN_SECONDS = 20L;
    static final long ANTI_SNIPE_WINDOW_SECONDS = 9L;
    static final long ANTI_SNIPE_EXTENSION_SECONDS = 5L;
    /** Card reveal before the bid clock starts (included in {@link #ROUND_DURATION_SECONDS}). */
    static final long ROUND_REVEAL_SECONDS = 2L;
    /** Bid window shown to players after reveal. */
    static final long ROUND_BID_DURATION_SECONDS = 30L;
    static final long ROUND_DURATION_SECONDS = ROUND_REVEAL_SECONDS + ROUND_BID_DURATION_SECONDS;
    static final long BID_GRACE_PERIOD_MS = 1500L;
    static final long TURN_OPEN_AFTER_SECONDS = 15L;

    private AuctionTiming() {}
}
