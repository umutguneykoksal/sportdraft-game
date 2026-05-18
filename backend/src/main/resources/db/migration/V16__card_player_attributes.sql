ALTER TABLE card
    ADD COLUMN pace INTEGER NOT NULL DEFAULT 50,
    ADD COLUMN shooting INTEGER NOT NULL DEFAULT 50,
    ADD COLUMN passing INTEGER NOT NULL DEFAULT 50,
    ADD COLUMN defending INTEGER NOT NULL DEFAULT 50,
    ADD COLUMN physical INTEGER NOT NULL DEFAULT 50;

ALTER TABLE card
    ADD CONSTRAINT chk_card_pace CHECK (pace BETWEEN 1 AND 99),
    ADD CONSTRAINT chk_card_shooting CHECK (shooting BETWEEN 1 AND 99),
    ADD CONSTRAINT chk_card_passing CHECK (passing BETWEEN 1 AND 99),
    ADD CONSTRAINT chk_card_defending CHECK (defending BETWEEN 1 AND 99),
    ADD CONSTRAINT chk_card_physical CHECK (physical BETWEEN 1 AND 99);

-- Derive attributes from primary position + overall rating (same rules as PlayerAttributes.fromOverall)
UPDATE card SET
    pace = LEAST(99, GREATEST(1, rating + CASE position
        WHEN 'GK' THEN -15 WHEN 'DEF' THEN -5 WHEN 'CDM' THEN -3
        WHEN 'CM' THEN 0 WHEN 'CAM' THEN 2 WHEN 'FWD' THEN 3 END)),
    shooting = LEAST(99, GREATEST(1, rating + CASE position
        WHEN 'GK' THEN -35 WHEN 'DEF' THEN -14 WHEN 'CDM' THEN -10
        WHEN 'CM' THEN -5 WHEN 'CAM' THEN 2 WHEN 'FWD' THEN 5 END)),
    passing = LEAST(99, GREATEST(1, rating + CASE position
        WHEN 'GK' THEN -5 WHEN 'DEF' THEN -2 WHEN 'CDM' THEN 0
        WHEN 'CM' THEN 4 WHEN 'CAM' THEN 5 WHEN 'FWD' THEN -3 END)),
    defending = LEAST(99, GREATEST(1, rating + CASE position
        WHEN 'GK' THEN 8 WHEN 'DEF' THEN 5 WHEN 'CDM' THEN 4
        WHEN 'CM' THEN 0 WHEN 'CAM' THEN -8 WHEN 'FWD' THEN -12 END)),
    physical = LEAST(99, GREATEST(1, rating + CASE position
        WHEN 'GK' THEN 2 WHEN 'DEF' THEN 4 WHEN 'CDM' THEN 3
        WHEN 'CM' THEN 1 WHEN 'CAM' THEN 0 WHEN 'FWD' THEN 2 END));

ALTER TABLE card
    ALTER COLUMN pace DROP DEFAULT,
    ALTER COLUMN shooting DROP DEFAULT,
    ALTER COLUMN passing DROP DEFAULT,
    ALTER COLUMN defending DROP DEFAULT,
    ALTER COLUMN physical DROP DEFAULT;
