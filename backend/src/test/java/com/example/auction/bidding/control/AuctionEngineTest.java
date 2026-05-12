package com.example.auction.bidding.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auction.bidding.boundary.AuctionRoundResponse;
import com.example.auction.bidding.entity.Position;
import java.time.Instant;
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
        AuctionRoundResponse round = new AuctionRoundResponse(
            UUID.randomUUID(),
            sessionId,
            UUID.randomUUID(),
            "Card",
            Position.CM.name(),
            80,
            0,
            null,
            null,
            null,
            null,
            Instant.now(),
            "OPEN",
            true
        );
        when(auctionService.startRound(sessionId)).thenReturn(round);

        AuctionRoundResponse response = auctionEngine.startRound(sessionId);

        verify(auctionService).startRound(sessionId);
        assertThat(response).isEqualTo(round);
    }
}
