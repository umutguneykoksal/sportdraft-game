-- Tag existing Premier League card pool rows for league-scoped auction pools.
ALTER TABLE card ADD COLUMN IF NOT EXISTS pool_league VARCHAR(32);

UPDATE card SET pool_league = 'PREMIER_LEAGUE' WHERE pool_league IS NULL;

ALTER TABLE card ALTER COLUMN pool_league SET DEFAULT 'PREMIER_LEAGUE';

UPDATE card SET pool_league = 'PREMIER_LEAGUE' WHERE pool_league IS NULL;

ALTER TABLE card ALTER COLUMN pool_league SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_card_pool_league_rating ON card (pool_league, rating);
