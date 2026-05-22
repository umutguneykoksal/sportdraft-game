package dev.sportdraft.integration;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

final class IntegrationTestStateCleaner {

    private static final String TRUNCATE_MUTABLE_TABLES = """
        TRUNCATE TABLE
          competition_match_view,
          competition_match,
          competition,
          match_history,
          match_result,
          bid,
          auction_round,
          session_player_cards,
          session_player,
          game_session,
          processed_event
        RESTART IDENTITY CASCADE
        """;

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;

    IntegrationTestStateCleaner(StringRedisTemplate redis, JdbcTemplate jdbc) {
        this.redis = redis;
        this.jdbc = jdbc;
    }

    void reset() {
        redis.execute((RedisCallback<Object>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        jdbc.execute(TRUNCATE_MUTABLE_TABLES);
    }
}
