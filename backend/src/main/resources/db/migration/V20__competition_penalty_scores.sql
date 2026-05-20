ALTER TABLE competition_match
    ADD COLUMN IF NOT EXISTS home_penalty_score INT,
    ADD COLUMN IF NOT EXISTS away_penalty_score INT;
