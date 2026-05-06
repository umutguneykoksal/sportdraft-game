ALTER TABLE auction_round
    ADD COLUMN IF NOT EXISTS ready_user_one_id UUID;

ALTER TABLE auction_round
    ADD COLUMN IF NOT EXISTS ready_user_two_id UUID;
