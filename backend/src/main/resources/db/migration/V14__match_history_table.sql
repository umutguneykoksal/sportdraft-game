CREATE TABLE match_history (
    id            UUID PRIMARY KEY,
    session_id    UUID NOT NULL UNIQUE,
    home_user_id  UUID NOT NULL,
    away_user_id  UUID NOT NULL,
    home_score    INT  NOT NULL,
    away_score    INT  NOT NULL,
    home_formation VARCHAR(30) NOT NULL,
    away_formation VARCHAR(30) NOT NULL,
    seed          BIGINT NOT NULL,
    played_at     TIMESTAMPTZ NOT NULL,
    replay_data   JSONB NOT NULL
);

CREATE INDEX idx_match_history_played_at ON match_history(played_at);
CREATE INDEX idx_match_history_home_user ON match_history(home_user_id);
CREATE INDEX idx_match_history_away_user ON match_history(away_user_id);
