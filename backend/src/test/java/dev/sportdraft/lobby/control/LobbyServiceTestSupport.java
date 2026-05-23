package dev.sportdraft.lobby.control;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import dev.sportdraft.session.redis.SessionLock;
import java.util.UUID;
import java.util.function.Supplier;

final class LobbyServiceTestSupport {

    private LobbyServiceTestSupport() {}

    static SessionLock passthroughSessionLock() {
        SessionLock guard = mock(SessionLock.class);
        lenient().doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(guard).runVoid(any(UUID.class), any(Runnable.class));
        lenient().doAnswer(invocation -> invocation.getArgument(1, Supplier.class).get())
            .when(guard).run(any(UUID.class), any(Supplier.class));
        return guard;
    }
}
