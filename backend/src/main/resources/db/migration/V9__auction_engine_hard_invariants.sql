CREATE UNIQUE INDEX IF NOT EXISTS uq_auction_round_session_card
    ON auction_round (session_id, card_id);

ALTER TABLE auction_round
    ADD CONSTRAINT chk_auction_round_highest_bid_non_negative
    CHECK (highest_bid_cents >= 0);
