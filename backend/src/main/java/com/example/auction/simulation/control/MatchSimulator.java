package com.example.auction.simulation.control;

import com.example.auction.bidding.entity.Position;
import com.example.auction.session.entity.Formation;
import com.example.auction.simulation.entity.MatchEvent;
import com.example.auction.simulation.entity.MatchEventType;
import com.example.auction.simulation.entity.SimulationResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MatchSimulator {

    /**
     * Simulates a match and returns full result with timeline.
     */
    public SimulationResult simulateWithTimeline(
        long seed,
        Formation homeFormation,
        Formation awayFormation,
        List<SessionFinalizedEvent.RatedCard> homeRoster,
        List<SessionFinalizedEvent.RatedCard> awayRoster,
        UUID homeTeamId,
        UUID awayTeamId
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

        List<MatchEvent> timeline = generateTimeline(
            rng, homeGoals, awayGoals,
            homeRoster, awayRoster,
            homeTeamId, awayTeamId
        );

        return new SimulationResult(homeGoals, awayGoals, timeline);
    }

    /**
     * Legacy method for backward compatibility - returns only score.
     */
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

    private List<MatchEvent> generateTimeline(
        Random rng,
        int homeGoals, int awayGoals,
        List<SessionFinalizedEvent.RatedCard> homeRoster,
        List<SessionFinalizedEvent.RatedCard> awayRoster,
        UUID homeTeamId, UUID awayTeamId
    ) {
        List<MatchEvent> events = new ArrayList<>();

        // Kick-off
        events.add(MatchEvent.generic(0, MatchEventType.KICK_OFF, "Match begins"));

        // Generate goal minutes (distributed across 90 minutes, avoiding half-time)
        List<Integer> homeGoalMinutes = generateGoalMinutes(rng, homeGoals);
        List<Integer> awayGoalMinutes = generateGoalMinutes(rng, awayGoals);

        // Add goals with preceding shots
        for (int minute : homeGoalMinutes) {
            addGoalSequence(events, rng, minute, homeTeamId, homeRoster, awayRoster, true);
        }
        for (int minute : awayGoalMinutes) {
            addGoalSequence(events, rng, minute, awayTeamId, awayRoster, homeRoster, false);
        }

        // Add missed shots / saved shots - ensure both teams have some
        int homeShotsExtra = 2 + rng.nextInt(4); // 2-5 extra shots for home
        int awayShotsExtra = 2 + rng.nextInt(4); // 2-5 extra shots for away
        
        for (int i = 0; i < homeShotsExtra; i++) {
            int minute = 1 + rng.nextInt(89);
            if (minute == 45) minute = 44;
            addMissedShot(events, rng, minute, homeTeamId, homeRoster);
        }
        for (int i = 0; i < awayShotsExtra; i++) {
            int minute = 1 + rng.nextInt(89);
            if (minute == 45) minute = 44;
            addMissedShot(events, rng, minute, awayTeamId, awayRoster);
        }

        // Add fouls - ensure both teams commit some
        int homeFouls = 1 + rng.nextInt(3); // 1-3 fouls for home
        int awayFouls = 1 + rng.nextInt(3); // 1-3 fouls for away
        
        for (int i = 0; i < homeFouls; i++) {
            int minute = 5 + rng.nextInt(80);
            if (minute == 45) minute = 46;
            addFoul(events, rng, minute, homeTeamId, homeRoster);
        }
        for (int i = 0; i < awayFouls; i++) {
            int minute = 5 + rng.nextInt(80);
            if (minute == 45) minute = 46;
            addFoul(events, rng, minute, awayTeamId, awayRoster);
        }

        // Half-time
        events.add(MatchEvent.generic(45, MatchEventType.HALF_TIME, "Half-time"));

        // Full-time
        events.add(MatchEvent.generic(90, MatchEventType.FULL_TIME, 
            "Full-time: " + homeGoals + " - " + awayGoals));

        // Sort by minute
        events.sort(Comparator.comparingInt(MatchEvent::minute));

        return events;
    }

    private List<Integer> generateGoalMinutes(Random rng, int goals) {
        List<Integer> minutes = new ArrayList<>();
        for (int i = 0; i < goals; i++) {
            int minute;
            do {
                minute = 1 + rng.nextInt(89); // 1-89
            } while (minute == 45 || minutes.contains(minute)); // Avoid half-time and duplicates
            minutes.add(minute);
        }
        Collections.sort(minutes);
        return minutes;
    }

    private void addGoalSequence(
        List<MatchEvent> events, Random rng, int minute,
        UUID teamId, List<SessionFinalizedEvent.RatedCard> attackers,
        List<SessionFinalizedEvent.RatedCard> defenders, boolean isHome
    ) {
        // Pick a scorer (prefer forwards/midfielders)
        SessionFinalizedEvent.RatedCard scorer = pickAttacker(rng, attackers);
        
        // 40% chance of a saved shot before the goal (same minute - 1)
        if (minute > 1 && rng.nextDouble() < 0.4) {
            SessionFinalizedEvent.RatedCard shooter = pickAttacker(rng, attackers);
            SessionFinalizedEvent.RatedCard keeper = pickKeeper(defenders);
            int shotMinute = minute - 1;
            if (shotMinute == 45) shotMinute = 44;
            events.add(MatchEvent.forPlayer(
                shotMinute, MatchEventType.SHOT_SAVED, teamId,
                shooter.id(), getPlayerName(shooter),
                "Shot saved by " + getPlayerName(keeper)
            ));
        }

        // The goal
        String[] goalDetails = {
            "Goal! Great finish",
            "Goal! Powerful shot",
            "Goal! Header from close range",
            "Goal! Clinical strike",
            "Goal! Unstoppable shot"
        };
        events.add(MatchEvent.forPlayer(
            minute, MatchEventType.GOAL, teamId,
            scorer.id(), getPlayerName(scorer),
            goalDetails[rng.nextInt(goalDetails.length)]
        ));
    }

    private void addMissedShot(
        List<MatchEvent> events, Random rng, int minute,
        UUID teamId, List<SessionFinalizedEvent.RatedCard> attackers
    ) {
        SessionFinalizedEvent.RatedCard shooter = pickAttacker(rng, attackers);
        boolean saved = rng.nextBoolean();
        
        if (saved) {
            String[] details = {"Shot saved", "Good save", "Diving save", "Comfortable save"};
            events.add(MatchEvent.forPlayer(
                minute, MatchEventType.SHOT_SAVED, teamId,
                shooter.id(), getPlayerName(shooter),
                details[rng.nextInt(details.length)]
            ));
        } else {
            String[] details = {"Shot wide", "Shot over the bar", "Effort goes wide", "Missed the target"};
            events.add(MatchEvent.forPlayer(
                minute, MatchEventType.SHOT_MISSED, teamId,
                shooter.id(), getPlayerName(shooter),
                details[rng.nextInt(details.length)]
            ));
        }
    }

    private void addFoul(
        List<MatchEvent> events, Random rng, int minute,
        UUID teamId, List<SessionFinalizedEvent.RatedCard> players
    ) {
        SessionFinalizedEvent.RatedCard fouler = players.get(rng.nextInt(players.size()));
        String[] details = {"Foul committed", "Late challenge", "Tactical foul", "Clumsy tackle"};
        events.add(MatchEvent.forPlayer(
            minute, MatchEventType.FOUL, teamId,
            fouler.id(), getPlayerName(fouler),
            details[rng.nextInt(details.length)]
        ));
    }

    private SessionFinalizedEvent.RatedCard pickAttacker(Random rng, List<SessionFinalizedEvent.RatedCard> roster) {
        // Prefer FWD, CAM, CM for goals/shots
        List<SessionFinalizedEvent.RatedCard> attackers = roster.stream()
            .filter(c -> c.position() == Position.FWD || c.position() == Position.CAM || c.position() == Position.CM)
            .toList();
        if (attackers.isEmpty()) {
            return roster.get(rng.nextInt(roster.size()));
        }
        return attackers.get(rng.nextInt(attackers.size()));
    }

    private SessionFinalizedEvent.RatedCard pickKeeper(List<SessionFinalizedEvent.RatedCard> roster) {
        return roster.stream()
            .filter(c -> c.position() == Position.GK)
            .findFirst()
            .orElse(roster.get(0));
    }

    private String getPlayerName(SessionFinalizedEvent.RatedCard card) {
        if (card.name() != null && !card.name().isBlank()) {
            return card.name();
        }
        return card.position().name() + " (" + card.rating() + ")";
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
