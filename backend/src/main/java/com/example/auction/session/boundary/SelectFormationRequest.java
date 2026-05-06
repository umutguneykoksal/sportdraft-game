package com.example.auction.session.boundary;

import com.example.auction.session.entity.Formation;
import jakarta.validation.constraints.NotNull;

public record SelectFormationRequest(@NotNull Formation formation) {
}
