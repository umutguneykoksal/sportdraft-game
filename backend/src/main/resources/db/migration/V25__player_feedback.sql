CREATE TABLE player_feedback (
    id          UUID PRIMARY KEY,
    category    VARCHAR(20) NOT NULL,
    message     TEXT NOT NULL,
    page_path   VARCHAR(200),
    app_version VARCHAR(40),
    user_agent  VARCHAR(512),
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_player_feedback_created_at ON player_feedback (created_at DESC);
