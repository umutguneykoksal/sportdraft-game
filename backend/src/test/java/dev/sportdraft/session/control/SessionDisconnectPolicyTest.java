package dev.sportdraft.session.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sportdraft.session.entity.GameMode;
import dev.sportdraft.session.entity.SessionStatus;
import dev.sportdraft.session.redis.ActiveSession;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class SessionDisconnectPolicyTest {

    @Test
    void shouldSkipDisconnectDraw_forMultiplayerAndCompetitionModes() {
        ActiveSession league = new ActiveSession();
        league.setGameMode(GameMode.LEAGUE);
        league.setMaxPlayers(4);
        league.setStatus(SessionStatus.AUCTIONING);

        assertThat(SessionDisconnectPolicy.shouldSkipDisconnectDraw(league)).isTrue();
    }

    @Test
    void finishedGracePeriod_usesTournamentRetention() {
        ActiveSession session = new ActiveSession();
        session.setGameMode(GameMode.TOURNAMENT);

        assertThat(SessionDisconnectPolicy.finishedGracePeriod(session, Duration.ofSeconds(240)))
            .isEqualTo(Duration.ofSeconds(240));
    }

    @Test
    void shouldResetLobbyAfterLeave_whenCountdownNotFull() {
        ActiveSession session = new ActiveSession();
        session.setMaxPlayers(4);
        session.getPlayers().add(new dev.sportdraft.session.redis.ActivePlayer());

        assertThat(SessionDisconnectPolicy.shouldResetLobbyAfterLeave(session, SessionStatus.COUNTDOWN)).isTrue();
        assertThat(SessionDisconnectPolicy.shouldResetLobbyAfterLeave(session, SessionStatus.WAITING)).isFalse();
    }
}
