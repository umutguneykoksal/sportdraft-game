ALTER TABLE session_player
    ADD CONSTRAINT uq_session_player_session_user UNIQUE (session_id, user_id);

CREATE UNIQUE INDEX uq_auction_round_open_per_session
    ON auction_round (session_id)
    WHERE status = 'OPEN';
