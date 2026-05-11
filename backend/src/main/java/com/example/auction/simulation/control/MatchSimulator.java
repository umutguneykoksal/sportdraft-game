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

    private double attackScore(Formation formation, List<SessionFinalizedEvent.RatedCard> roster) {
        Map<Position, Double> avg = averages(roster);
        double score = avg.getOrDefault(Position.FWD, 40.0) * 0.4
            + avg.getOrDefault(Position.CAM, 40.0) * 0.3
            + avg.getOrDefault(Position.CM, 40.0) * 0.2
            + avg.getOrDefault(Position.CDM, 40.0) * 0.1;
        score *= formation.getAttackMod();
        return score;
    }

    private double defenseScore(Formation formation, List<SessionFinalizedEvent.RatedCard> roster) {
        Map<Position, Double> avg = averages(roster);
        double score = avg.getOrDefault(Position.DEF, 40.0) * 0.4
            + avg.getOrDefault(Position.GK, 40.0) * 0.3
            + avg.getOrDefault(Position.CDM, 40.0) * 0.2
            + avg.getOrDefault(Position.CM, 40.0) * 0.1;
        score *= formation.getDefenseMod();
        return score;
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
