package dev.sportdraft.session.boundary;

import dev.sportdraft.session.entity.Formation;
import jakarta.validation.constraints.NotNull;

public record SelectFormationRequest(@NotNull Formation formation) {
}
