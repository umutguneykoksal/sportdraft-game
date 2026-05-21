package com.example.auction.competition.control;

import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.simulation.control.DraftCompletedEvent;

@FunctionalInterface
interface CompetitionInitializer {
    CompetitionEntity initialize(DraftCompletedEvent event);
}
