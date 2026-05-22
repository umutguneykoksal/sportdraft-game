package dev.sportdraft.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.sportdraft.bidding.boundary.AuctionRoundResponse;
import dev.sportdraft.bidding.entity.Position;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuctionEngineTest {

    @Mock
    private AuctionService auctionService;
    @InjectMocks
    private AuctionEngine auctionEngine;

    @Test
    void startRoundDelegatesToAuctionService() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AuctionRoundResponse round = new AuctionRoundResponse(
            UUID.randomUUID(),
            sessionId,
            1L,
            UUID.randomUUID(),
            "Card",
            "Manchester City",
            Position.CM.name(),
            List.of(Position.CM.name()),
            80,
            75, 78, 82, 70, 74,
            0,
            null,
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            null,
            "OPEN",
            true,
            true,
            false,
            true,
            true,
            List.of()
        );
        when(auctionService.startRound(sessionId, userId)).thenReturn(round);

        AuctionRoundResponse response = auctionEngine.startRound(sessionId, userId);

        verify(auctionService).startRound(sessionId, userId);
        assertThat(response).isEqualTo(round);
    }
}
