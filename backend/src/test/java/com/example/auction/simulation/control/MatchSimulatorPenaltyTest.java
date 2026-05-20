package com.example.auction.simulation.control;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auction.bidding.entity.PlayerAttributes;
import com.example.auction.bidding.entity.Position;
import com.example.auction.session.entity.Formation;
import com.example.auction.simulation.entity.MatchEventType;
import com.example.auction.simulation.entity.SimulationResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchSimulatorPenaltyTest {

    @Test
    void drawTriggersPenaltyShootoutWithWinner() {
        MatchSimulator simulator = new MatchSimulator();
        long seed = 424242L;
        List<SessionFinalizedEvent.RatedCard> home = List.of(
            rated(Position.FWD, 85, "Home Striker"),
            rated(Position.GK, 80, "Home GK")
        );
        List<SessionFinalizedEvent.RatedCard> away = List.of(
            rated(Position.FWD, 84, "Away Striker"),
            rated(Position.GK, 79, "Away GK")
        );
        UUID homeId = UUID.randomUUID();
        UUID awayId = UUID.randomUUID();

        SimulationResult result = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            SimulationResult candidate = simulator.simulateWithTimeline(
                seed + attempt,
                Formation.FOUR_FOUR_TWO,
                Formation.FOUR_FOUR_TWO,
                home,
                away,
                homeId,
                awayId
            );
            if (candidate.homeGoals() == candidate.awayGoals()) {
                result = candidate;
                break;
            }
        }

        assertThat(result).isNotNull();
        assertThat(result.decidedOnPenalties()).isTrue();
        assertThat(result.homePenaltyGoals()).isNotEqualTo(result.awayPenaltyGoals());
        assertThat(result.timeline()).anyMatch(e -> e.type() == MatchEventType.PENALTIES_START);
        assertThat(result.timeline()).anyMatch(e -> e.type() == MatchEventType.PENALTY_SCORED);
        assertThat(result.resolveWinner(homeId, awayId)).isNotNull();
    }

    private static SessionFinalizedEvent.RatedCard rated(Position position, int rating, String name) {
        return new SessionFinalizedEvent.RatedCard(
            UUID.randomUUID(),
            position,
            rating,
            name,
            new PlayerAttributes(rating, rating, rating, rating, rating)
        );
    }
}
