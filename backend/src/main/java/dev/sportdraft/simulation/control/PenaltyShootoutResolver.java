package dev.sportdraft.simulation.control;

import dev.sportdraft.bidding.entity.Position;
import dev.sportdraft.simulation.entity.MatchEvent;
import dev.sportdraft.simulation.entity.MatchEventType;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Deterministic penalty shoot-out resolution appended to a match timeline. */
class PenaltyShootoutResolver {

    record Outcome(int homePenaltyGoals, int awayPenaltyGoals) {}

    Outcome resolve(
        Random rng,
        List<MatchEvent> events,
        List<SessionFinalizedEvent.RatedCard> homeRoster,
        List<SessionFinalizedEvent.RatedCard> awayRoster,
        UUID homeTeamId,
        UUID awayTeamId
    ) {
        events.add(MatchEvent.generic(91, MatchEventType.PENALTIES_START, "Penalty shoot-out begins"));

        SessionFinalizedEvent.RatedCard homeKeeper = pickKeeper(homeRoster);
        SessionFinalizedEvent.RatedCard awayKeeper = pickKeeper(awayRoster);

        int homePens = 0;
        int awayPens = 0;
        int kick = 0;

        for (int round = 0; round < 5; round++) {
            kick++;
            homePens += takePenaltyKick(events, rng, 91 + kick, homeTeamId, homeRoster, awayKeeper);
            if (isPenaltyShootoutDecided(homePens, awayPens, round + 1, 5)) {
                break;
            }
            kick++;
            awayPens += takePenaltyKick(events, rng, 91 + kick, awayTeamId, awayRoster, homeKeeper);
            if (isPenaltyShootoutDecided(homePens, awayPens, round + 1, 5)) {
                break;
            }
        }

        while (homePens == awayPens) {
            kick++;
            homePens += takePenaltyKick(events, rng, 91 + kick, homeTeamId, homeRoster, awayKeeper);
            if (homePens != awayPens) {
                break;
            }
            kick++;
            awayPens += takePenaltyKick(events, rng, 91 + kick, awayTeamId, awayRoster, homeKeeper);
        }

        String winner = homePens > awayPens ? "Home" : "Away";
        events.add(MatchEvent.generic(
            91 + kick + 1,
            MatchEventType.PENALTIES_END,
            winner + " wins on penalties " + homePens + " - " + awayPens
        ));

        events.sort(Comparator.comparingInt(MatchEvent::minute));
        return new Outcome(homePens, awayPens);
    }

    private boolean isPenaltyShootoutDecided(int homePens, int awayPens, int roundsCompleted, int totalRounds) {
        int remainingHome = totalRounds - roundsCompleted;
        int remainingAway = totalRounds - roundsCompleted;
        if (homePens > awayPens + remainingAway) {
            return true;
        }
        return awayPens > homePens + remainingHome;
    }

    private int takePenaltyKick(
        List<MatchEvent> events,
        Random rng,
        int minute,
        UUID teamId,
        List<SessionFinalizedEvent.RatedCard> attackers,
        SessionFinalizedEvent.RatedCard opposingKeeper
    ) {
        SessionFinalizedEvent.RatedCard taker = pickPenaltyTaker(rng, attackers);
        double keeperDef = opposingKeeper.attributes() != null
            ? opposingKeeper.attributes().defending()
            : opposingKeeper.rating();
        double takerShoot = taker.attributes() != null
            ? taker.attributes().shooting()
            : taker.rating();
        double successChance = clamp(0.72 + (takerShoot - keeperDef) * 0.004, 0.38, 0.92);

        if (rng.nextDouble() < successChance) {
            events.add(MatchEvent.forPlayer(
                minute, MatchEventType.PENALTY_SCORED, teamId,
                taker.id(), playerName(taker),
                "Scores from the spot!"
            ));
            return 1;
        }
        events.add(MatchEvent.forPlayer(
            minute, MatchEventType.PENALTY_MISSED, teamId,
            taker.id(), playerName(taker),
            "Penalty saved / missed!"
        ));
        return 0;
    }

    private SessionFinalizedEvent.RatedCard pickPenaltyTaker(
        Random rng,
        List<SessionFinalizedEvent.RatedCard> roster
    ) {
        List<SessionFinalizedEvent.RatedCard> candidates = roster.stream()
            .filter(c -> c.position() != Position.GK)
            .sorted(Comparator.comparingInt((SessionFinalizedEvent.RatedCard c) -> {
                if (c.attributes() != null) {
                    return c.attributes().shooting();
                }
                return c.rating();
            }).reversed())
            .toList();
        if (candidates.isEmpty()) {
            return roster.get(rng.nextInt(roster.size()));
        }
        int pool = Math.min(5, candidates.size());
        return candidates.get(rng.nextInt(pool));
    }

    private SessionFinalizedEvent.RatedCard pickKeeper(List<SessionFinalizedEvent.RatedCard> roster) {
        return roster.stream()
            .filter(c -> c.position() == Position.GK)
            .findFirst()
            .orElseGet(() -> roster.get(0));
    }

    private String playerName(SessionFinalizedEvent.RatedCard card) {
        return card.name() != null && !card.name().isBlank() ? card.name() : "Player";
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
