package dev.sportdraft.session.entity;

/** Minimum card counts required for a league + rating pool to be selectable in the lobby. */
public final class CardPoolRules {

    public static final int MIN_CARD_COUNT = 100;

    private CardPoolRules() {}

    public static boolean isViable(int cardCount) {
        return cardCount >= MIN_CARD_COUNT;
    }
}
