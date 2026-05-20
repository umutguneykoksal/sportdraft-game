CREATE TABLE competition (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL UNIQUE,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    champion_user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE competition_match (
    id UUID PRIMARY KEY,
    competition_id UUID NOT NULL REFERENCES competition(id) ON DELETE CASCADE,
    round_key VARCHAR(32) NOT NULL,
    home_user_id UUID NOT NULL,
    away_user_id UUID NOT NULL,
    home_score INT,
    away_score INT,
    status VARCHAR(32) NOT NULL,
    seed BIGINT NOT NULL,
    sort_order INT NOT NULL
);

CREATE INDEX idx_competition_match_competition ON competition_match(competition_id);
