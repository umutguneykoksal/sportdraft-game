package com.example.auction.simulation.control;

import com.example.auction.bidding.entity.Position;
import com.example.auction.session.entity.Formation;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class MatchSimulator {

    public Score simulate(
        long seed,
        Formation homeFormation,
        Formation awayFormation,
        List<SessionFinalizedEvent.RatedCard> homeRoster,
        List<SessionFinalizedEvent.RatedCard> awayRoster
    ) {
        Random rng = new Random(seed);

        double homeAttack = attackScore(homeFormation, homeRoster);
        double awayAttack = attackScore(awayFormation, awayRoster);
        double homeDefense = defenseScore(homeFormation, homeRoster);
        double awayDefense = defenseScore(awayFormation, awayRoster);

        double homeLambda = clamp(1.4 * (homeAttack / Math.max(awayDefense, 1.0)), 0.2, 4.5);
        double awayLambda = clamp(1.4 * (awayAttack / Math.max(homeDefense, 1.0)), 0.2, 4.5);

        int homeGoals = poissonSample(homeLambda, rng);
        int awayGoals = poissonSample(awayLambda, rng);
        return new Score(homeGoals, awayGoals);
    }

    private static final Map<Position, Double> ATTACK_WEIGHT = Map.of(
        Position.FWD, 1.0,
        Position.CAM, 0.8,
        Position.CM,  0.4,
        Position.CDM, 0.15,
        Position.DEF, 0.05,
        Position.GK,  0.0
    );

    private static final Map<Position, Double> DEFENSE_WEIGHT = Map.of(
        Position.GK,  1.0,
        Position.DEF, 0.9,
        Position.CDM, 0.6,
        Position.CM,  0.3,
        Position.CAM, 0.1,
        Position.FWD, 0.0
    );

    private double attackScore(Formation formation, List<SessionFinalizedEvent.RatedCard> roster) {
        return weightedScore(formation, roster, ATTACK_WEIGHT) * formation.getAttackMod();
    }

    private double defenseScore(Formation formation, List<SessionFinalizedEvent.RatedCard> roster) {
        return weightedScore(formation, roster, DEFENSE_WEIGHT) * formation.getDefenseMod();
    }

    /**
     * Computes a weighted average rating using only positions the formation
     * actually contains. Each position's contribution scales by its slot count
     * and role-based weight, so formations without CDM/CAM are not penalised
     * by phantom 40-rated defaults.
     */
    private double weightedScore(
        Formation formation,
        List<SessionFinalizedEvent.RatedCard> roster,
        Map<Position, Double> roleWeights
    ) {
        Map<Position, Double> avg = averages(roster);
        double weightedSum = 0;
        double totalWeight = 0;
        for (Position pos : Position.values()) {
            int slots = formation.slotCount(pos);
            if (slots == 0) continue;
            double w = roleWeights.getOrDefault(pos, 0.0) * slots;
            weightedSum += avg.getOrDefault(pos, 40.0) * w;
            totalWeight += w;
        }
        return totalWeight > 0 ? weightedSum / totalWeight : 40.0;
    }

    private Map<Position, Double> averages(List<SessionFinalizedEvent.RatedCard> roster) {
        Map<Position, Integer> total = new EnumMap<>(Position.class);
        Map<Position, Integer> count = new EnumMap<>(Position.class);
        for (SessionFinalizedEvent.RatedCard card : roster) {
            total.merge(card.position(), card.rating(), Integer::sum);
            count.merge(card.position(), 1, Integer::sum);
        }
        Map<Position, Double> avg = new EnumMap<>(Position.class);
        for (Position pos : Position.values()) {
            int c = count.getOrDefault(pos, 0);
            avg.put(pos, c == 0 ? 40.0 : (double) total.get(pos) / c);
        }
        return avg;
    }

    private int poissonSample(double lambda, Random rng) {
        double l = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        do {
            k++;
            p *= rng.nextDouble();
        } while (p > l && k < 15);
        return k - 1;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Score(int homeGoals, int awayGoals) {}
}
