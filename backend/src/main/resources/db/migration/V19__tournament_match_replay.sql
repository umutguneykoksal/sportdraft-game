ALTER TABLE competition
    ADD COLUMN IF NOT EXISTS draft_snapshot JSONB;

ALTER TABLE competition_match
    ADD COLUMN IF NOT EXISTS winner_user_id UUID,
    ADD COLUMN IF NOT EXISTS replay_data JSONB;

CREATE TABLE IF NOT EXISTS competition_match_view (
    match_id UUID NOT NULL REFERENCES competition_match(id) ON DELETE CASCADE,
    user_id UUID NOT NULL,
    watched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (match_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_competition_match_view_match ON competition_match_view(match_id);
