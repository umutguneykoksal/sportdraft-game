CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS game_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status VARCHAR(40) NOT NULL,
    countdown_ends_at TIMESTAMPTZ,
    match_seed BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS card (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    position VARCHAR(10) NOT NULL,
    rating INTEGER NOT NULL,
    CONSTRAINT chk_card_rating CHECK (rating BETWEEN 1 AND 100)
);

CREATE TABLE IF NOT EXISTS session_player (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES game_session(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    formation VARCHAR(30) NOT NULL,
    remaining_budget_cents BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS session_player_cards (
    session_player_id UUID NOT NULL REFERENCES session_player(id) ON DELETE CASCADE,
    card_id UUID NOT NULL REFERENCES card(id),
    PRIMARY KEY (session_player_id, card_id)
);

CREATE TABLE IF NOT EXISTS auction_round (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES game_session(id) ON DELETE CASCADE,
    card_id UUID NOT NULL REFERENCES card(id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    highest_bid_cents BIGINT NOT NULL DEFAULT 0,
    highest_bidder_id UUID,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS bid (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id UUID NOT NULL REFERENCES auction_round(id) ON DELETE CASCADE,
    bidder_id UUID NOT NULL REFERENCES session_player(id) ON DELETE CASCADE,
    amount_cents BIGINT NOT NULL,
    idempotency_key UUID NOT NULL,
    placed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (round_id, idempotency_key)
);

CREATE TABLE IF NOT EXISTS match_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL UNIQUE REFERENCES game_session(id) ON DELETE CASCADE,
    home_score INTEGER NOT NULL,
    away_score INTEGER NOT NULL,
    seed BIGINT NOT NULL,
    simulated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS processed_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
