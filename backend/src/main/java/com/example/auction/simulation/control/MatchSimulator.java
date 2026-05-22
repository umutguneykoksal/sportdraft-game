package com.example.auction.simulation.control;

import com.example.auction.bidding.entity.PlayerAttributes;
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

    private final PenaltyShootoutResolver penaltyShootoutResolver = new PenaltyShootoutResolver();

    private record TeamContext(
        UUID teamId,
        List<SessionFinalizedEvent.RatedCard> roster,
        List<SessionFinalizedEvent.RatedCard> opposingRoster
    ) {}

    private record ScorePair(int homeGoals, int awayGoals) {}

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
        return simulateWithTimeline(
            seed, homeFormation, awayFormation, homeRoster, awayRoster, homeTeamId, awayTeamId, false
        );
    }

    public SimulationResult simulateWithTimeline(
        long seed,
        Formation homeFormation,
        Formation awayFormation,
        List<SessionFinalizedEvent.RatedCard> homeRoster,
        List<SessionFinalizedEvent.RatedCard> awayRoster,
        UUID homeTeamId,
        UUID awayTeamId,
        boolean allowDraws
    ) {
        Random rng = new Random(seed);
        ScorePair score = computeScore(rng, homeFormation, awayFormation, homeRoster, awayRoster);

        List<MatchEvent> timeline = generateTimeline(
            rng, score.homeGoals(), score.awayGoals(),
            homeRoster, awayRoster,
            homeTeamId, awayTeamId
        );

        int homePenaltyGoals = 0;
        int awayPenaltyGoals = 0;
        if (score.homeGoals() == score.awayGoals() && !allowDraws) {
            PenaltyShootoutResolver.Outcome pens = penaltyShootoutResolver.resolve(
                rng, timeline, homeRoster, awayRoster, homeTeamId, awayTeamId
            );
            homePenaltyGoals = pens.homePenaltyGoals();
            awayPenaltyGoals = pens.awayPenaltyGoals();
        }

        return new SimulationResult(
            score.homeGoals(), score.awayGoals(), timeline, homePenaltyGoals, awayPenaltyGoals
        );
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
        ScorePair score = computeScore(new Random(seed), homeFormation, awayFormation, homeRoster, awayRoster);
        return new Score(score.homeGoals(), score.awayGoals());
    }

    private ScorePair computeScore(
        Random rng,
        Formation homeFormation,
        Formation awayFormation,
        List<SessionFinalizedEvent.RatedCard> homeRoster,
        List<SessionFinalizedEvent.RatedCard> awayRoster
    ) {
        double homeAttack = attackScore(homeFormation, homeRoster);
        double awayAttack = attackScore(awayFormation, awayRoster);
        double homeDefense = defenseScore(homeFormation, homeRoster);
        double awayDefense = defenseScore(awayFormation, awayRoster);

        double homeLambda = clamp(1.4 * (homeAttack / Math.max(awayDefense, 1.0)), 0.2, 4.5);
        double awayLambda = clamp(1.4 * (awayAttack / Math.max(homeDefense, 1.0)), 0.2, 4.5);

        return new ScorePair(poissonSample(homeLambda, rng), poissonSample(awayLambda, rng));
    }

    private List<MatchEvent> generateTimeline(
        Random rng,
        int homeGoals, int awayGoals,
        List<SessionFinalizedEvent.RatedCard> homeRoster,
        List<SessionFinalizedEvent.RatedCard> awayRoster,
        UUID homeTeamId, UUID awayTeamId
    ) {
        List<MatchEvent> events = new ArrayList<>();

        events.add(MatchEvent.generic(0, MatchEventType.KICK_OFF, "Match begins"));

        TeamContext home = new TeamContext(homeTeamId, homeRoster, awayRoster);
        TeamContext away = new TeamContext(awayTeamId, awayRoster, homeRoster);

        addGoalsForTeam(events, rng, home, generateGoalMinutes(rng, homeGoals));
        addGoalsForTeam(events, rng, away, generateGoalMinutes(rng, awayGoals));

        addExtraShotsForTeam(events, rng, home, 2 + rng.nextInt(4));
        addExtraShotsForTeam(events, rng, away, 2 + rng.nextInt(4));

        addFoulsForTeam(events, rng, home, 1 + rng.nextInt(3));
        addFoulsForTeam(events, rng, away, 1 + rng.nextInt(3));

        events.add(MatchEvent.generic(45, MatchEventType.HALF_TIME, "Half-time"));
        events.add(MatchEvent.generic(90, MatchEventType.FULL_TIME,
            "Full-time: " + homeGoals + " - " + awayGoals));

        events.sort(Comparator.comparingInt(MatchEvent::minute));
        return events;
    }

    private void addGoalsForTeam(
        List<MatchEvent> events,
        Random rng,
        TeamContext team,
        List<Integer> goalMinutes
    ) {
        for (int minute : goalMinutes) {
            addGoalSequence(events, rng, minute, team.teamId(), team.roster(), team.opposingRoster());
        }
    }

    private void addExtraShotsForTeam(
        List<MatchEvent> events,
        Random rng,
        TeamContext team,
        int count
    ) {
        for (int i = 0; i < count; i++) {
            int minute = 1 + rng.nextInt(89);
            if (minute == 45) {
                minute = 44;
            }
            addMissedShot(events, rng, minute, team.teamId(), team.roster());
        }
    }

    private void addFoulsForTeam(
        List<MatchEvent> events,
        Random rng,
        TeamContext team,
        int count
    ) {
        for (int i = 0; i < count; i++) {
            int minute = 5 + rng.nextInt(80);
            if (minute == 45) {
                minute = 46;
            }
            addFoul(events, rng, minute, team.teamId(), team.roster());
        }
    }

    private List<Integer> generateGoalMinutes(Random rng, int goals) {
        List<Integer> minutes = new ArrayList<>();
        for (int i = 0; i < goals; i++) {
            int minute;
            do {
                minute = 1 + rng.nextInt(89);
            } while (minute == 45 || minutes.contains(minute));
            minutes.add(minute);
        }
        Collections.sort(minutes);
        return minutes;
    }

    private void addGoalSequence(
        List<MatchEvent> events, Random rng, int minute,
        UUID teamId, List<SessionFinalizedEvent.RatedCard> attackers,
        List<SessionFinalizedEvent.RatedCard> defenders
    ) {
        SessionFinalizedEvent.RatedCard scorer = pickAttacker(rng, attackers);

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
        return weightedStrength(formation, roster, ATTACK_WEIGHT, PlayerStrength::attack) * formation.getAttackMod();
    }

    private double defenseScore(Formation formation, List<SessionFinalizedEvent.RatedCard> roster) {
        return weightedStrength(formation, roster, DEFENSE_WEIGHT, PlayerStrength::defense) * formation.getDefenseMod();
    }

    @FunctionalInterface
    private interface StrengthFn {
        double apply(Position position, PlayerAttributes attrs);
    }

    private double weightedStrength(
        Formation formation,
        List<SessionFinalizedEvent.RatedCard> roster,
        Map<Position, Double> roleWeights,
        StrengthFn strengthFn
    ) {
        Map<Position, Double> avg = averageStrengthByPosition(roster, strengthFn);
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

    private Map<Position, Double> averageStrengthByPosition(
        List<SessionFinalizedEvent.RatedCard> roster,
        StrengthFn strengthFn
    ) {
        Map<Position, Double> sum = new EnumMap<>(Position.class);
        Map<Position, Integer> count = new EnumMap<>(Position.class);
        for (SessionFinalizedEvent.RatedCard card : roster) {
            double value = strengthFn.apply(card.position(), card.attributes());
            sum.merge(card.position(), value, Double::sum);
            count.merge(card.position(), 1, Integer::sum);
        }
        Map<Position, Double> avg = new EnumMap<>(Position.class);
        for (Position pos : Position.values()) {
            int c = count.getOrDefault(pos, 0);
            avg.put(pos, c == 0 ? 40.0 : sum.get(pos) / c);
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
