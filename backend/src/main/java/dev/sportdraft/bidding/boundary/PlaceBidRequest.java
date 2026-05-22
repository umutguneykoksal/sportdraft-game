package dev.sportdraft.bidding.boundary;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PlaceBidRequest(
    @NotNull UUID roundId,
    @Min(1) long amountCents
) {
}
