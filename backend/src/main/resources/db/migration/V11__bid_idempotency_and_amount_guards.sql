ALTER TABLE bid
    ADD CONSTRAINT chk_bid_amount_positive
    CHECK (amount_cents > 0);

ALTER TABLE session_player
    ADD CONSTRAINT chk_session_player_budget_non_negative
    CHECK (remaining_budget_cents >= 0);
