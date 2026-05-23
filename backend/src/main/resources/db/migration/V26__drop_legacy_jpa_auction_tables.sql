-- Live auction state moved to Redis (ActiveSession). These JPA-era tables are unused.

DROP TABLE IF EXISTS bid;
DROP TABLE IF EXISTS auction_round;
DROP TABLE IF EXISTS session_player_cards;
DROP TABLE IF EXISTS session_player;
DROP TABLE IF EXISTS match_result;
DROP TABLE IF EXISTS game_session;
