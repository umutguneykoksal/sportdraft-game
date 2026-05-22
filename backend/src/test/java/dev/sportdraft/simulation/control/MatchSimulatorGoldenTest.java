package dev.sportdraft.simulation.control;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sportdraft.bidding.entity.PlayerAttributes;
import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.session.entity.Formation;
import dev.sportdraft.simulation.entity.SimulationResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchSimulatorGoldenTest {

    @Test
    void simulateWithTimeline_isDeterministicForFixedSeed() {
        MatchSimulator simulator = new MatchSimulator();
        UUID homeId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID awayId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        List<SessionFinalizedEvent.RatedCard> home = List.of(
            rated(Position.FWD, 82, "Alpha"),
            rated(Position.GK, 78, "Alpha GK")
        );
        List<SessionFinalizedEvent.RatedCard> away = List.of(
            rated(Position.FWD, 80, "Beta"),
            rated(Position.GK, 76, "Beta GK")
        );

        SimulationResult first = simulator.simulateWithTimeline(
            991122L,
            Formation.FOUR_FOUR_TWO,
            Formation.FOUR_THREE_THREE,
            home,
            away,
            homeId,
            awayId,
            true
        );
        SimulationResult second = simulator.simulateWithTimeline(
            991122L,
            Formation.FOUR_FOUR_TWO,
            Formation.FOUR_THREE_THREE,
            home,
            away,
            homeId,
            awayId,
            true
        );

        assertThat(second.homeGoals()).isEqualTo(first.homeGoals());
        assertThat(second.awayGoals()).isEqualTo(first.awayGoals());
        assertThat(second.timeline()).isEqualTo(first.timeline());
        assertThat(timelineFingerprint(second)).isEqualTo(timelineFingerprint(first));
    }

    private static String timelineFingerprint(SimulationResult result) {
        return result.homeGoals() + ":" + result.awayGoals() + ":" + result.timeline().size();
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
