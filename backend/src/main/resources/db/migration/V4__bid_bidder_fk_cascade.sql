ALTER TABLE bid
    DROP CONSTRAINT IF EXISTS bid_bidder_id_fkey;

ALTER TABLE bid
    ADD CONSTRAINT bid_bidder_id_fkey
        FOREIGN KEY (bidder_id)
        REFERENCES session_player(id)
        ON DELETE CASCADE;
