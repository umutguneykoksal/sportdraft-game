package dev.sportdraft.lobby.control;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import dev.sportdraft.session.redis.SessionMutationGuard;
import java.util.UUID;
import java.util.function.Supplier;

final class LobbyServiceTestSupport {

    private LobbyServiceTestSupport() {}

    static SessionMutationGuard passthroughMutationGuard() {
        SessionMutationGuard guard = mock(SessionMutationGuard.class);
        lenient().doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(guard).mutateVoid(any(UUID.class), any(Runnable.class));
        lenient().doAnswer(invocation -> invocation.getArgument(1, Supplier.class).get())
            .when(guard).mutate(any(UUID.class), any(Supplier.class));
        return guard;
    }
}
