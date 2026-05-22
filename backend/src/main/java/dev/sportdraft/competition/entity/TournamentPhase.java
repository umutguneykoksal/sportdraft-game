package dev.sportdraft.competition.entity;

public enum TournamentPhase {
    /** Each player watches their own semi-final once. */
    SEMIS,
    /** 15s break — semi scores visible to everyone, no match playback. */
    INTERMISSION,
    /** Mandatory final watch for all players. */
    FINAL,
    /** All three matches available as replays. */
    REPLAYS;

    public static TournamentPhase effective(TournamentPhase stored) {
        return stored != null ? stored : SEMIS;
    }
}
