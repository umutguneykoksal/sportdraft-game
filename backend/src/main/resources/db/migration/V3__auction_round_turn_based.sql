ALTER TABLE auction_round
    ADD COLUMN IF NOT EXISTS opening_bidder_user_id UUID;

ALTER TABLE auction_round
    ADD COLUMN IF NOT EXISTS current_turn_user_id UUID;
