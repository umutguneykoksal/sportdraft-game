package com.example.auction.lobby.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.common.realtime.SessionEventLog;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.GameMode;
import com.example.auction.session.entity.PoolLeague;
import com.example.auction.session.redis.ActiveSessionStore;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LobbyServicePoolGuardTest {

    @Mock
    private ActiveSessionStore sessionStore;
    @Mock
    private SseEmitterRegistry sseEmitterRegistry;
    @Mock
    private CardCatalogService cardCatalog;
    @Mock
    private SessionEventLog eventLog;
    @Mock
    private AuctionService auctionService;

    private LobbyService lobbyService;

    @BeforeEach
    void setUp() {
        lobbyService = new LobbyService(
            sessionStore,
            sseEmitterRegistry,
            cardCatalog,
            eventLog,
            auctionService
        );
        when(sessionStore.findAllActiveIds()).thenReturn(java.util.List.of());
    }

    @Test
    void createRoom_rejectsWhenPoolTooSmall() {
        when(cardCatalog.countCardsForPool(PoolLeague.SUPER_LIG, 80)).thenReturn(30);

        assertThatThrownBy(() -> lobbyService.createRoom(
            UUID.randomUUID(), "Host", GameMode.DUEL, 80, PoolLeague.SUPER_LIG.name()
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException rse = (ResponseStatusException) ex;
                assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(rse.getReason()).contains("Not enough cards");
                assertThat(rse.getReason()).contains("80");
            });
    }
}
