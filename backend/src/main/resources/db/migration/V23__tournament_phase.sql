ALTER TABLE competition
    ADD COLUMN IF NOT EXISTS tournament_phase VARCHAR(32) NOT NULL DEFAULT 'SEMIS',
    ADD COLUMN IF NOT EXISTS intermission_ends_at TIMESTAMPTZ;

UPDATE competition SET tournament_phase = 'REPLAYS' WHERE status = 'COMPLETED';
