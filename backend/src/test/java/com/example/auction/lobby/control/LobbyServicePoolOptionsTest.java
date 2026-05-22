package com.example.auction.lobby.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.auction.bidding.control.AuctionService;
import com.example.auction.bidding.control.CardCatalogService;
import com.example.auction.common.realtime.SessionEventLog;
import com.example.auction.common.sse.SseEmitterRegistry;
import com.example.auction.session.entity.PoolLeague;
import com.example.auction.session.redis.ActiveSessionStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LobbyServicePoolOptionsTest {

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
            auctionService,
            LobbyServiceTestSupport.passthroughMutationGuard()
        );
    }

    @Test
    void getPoolMinRatingOptions_marksThresholdsBelow100AsNotSelectable() {
        when(cardCatalog.countCardsForPool(PoolLeague.SUPER_LIG, 70)).thenReturn(469);
        when(cardCatalog.countCardsForPool(PoolLeague.SUPER_LIG, 75)).thenReturn(120);
        when(cardCatalog.countCardsForPool(PoolLeague.SUPER_LIG, 80)).thenReturn(30);

        var response = lobbyService.getPoolMinRatingOptions("SUPER_LIG");

        assertThat(response.poolLeague()).isEqualTo("SUPER_LIG");
        assertThat(response.options()).hasSize(3);
        assertThat(response.options().get(0).minRating()).isEqualTo(70);
        assertThat(response.options().get(0).selectable()).isTrue();
        assertThat(response.options().get(1).minRating()).isEqualTo(75);
        assertThat(response.options().get(1).selectable()).isTrue();
        assertThat(response.options().get(2).minRating()).isEqualTo(80);
        assertThat(response.options().get(2).cardCount()).isEqualTo(30);
        assertThat(response.options().get(2).selectable()).isFalse();
    }
}
