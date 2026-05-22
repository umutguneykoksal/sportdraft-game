package com.example.auction.competition.control;

import com.example.auction.competition.boundary.LeagueStandingRow;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LeagueStandingsService {

    public static final int WIN_POINTS = 3;
    public static final int DRAW_POINTS = 1;
    public static final String PLAYOFF_ROUND_KEY = LeagueRules.PLAYOFF_ROUND_KEY;

    public List<LeagueStandingRow> computeStandings(
        CompetitionDraftSnapshot snapshot,
        List<CompetitionMatchEntity> matches
    ) {
        Map<UUID, MutableStanding> rows = new HashMap<>();
        for (CompetitionDraftSnapshot.PlayerSnapshot player : snapshot.players()) {
            rows.put(player.userId(), new MutableStanding(player.userId(), player.nickname()));
        }

        for (CompetitionMatchEntity match : matches) {
            if (LeagueRules.isPlayoff(match.getRoundKey())) {
                continue;
            }
            if (match.getStatus() != CompetitionMatchStatus.COMPLETED) {
                continue;
            }
            applyMatchResult(rows, match);
        }

        Comparator<MutableStanding> comparator = buildComparator(matches);
        List<MutableStanding> sorted = rows.values().stream()
            .sorted(comparator)
            .toList();

        List<LeagueStandingRow> result = new ArrayList<>();
        int rank = 1;
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0 && comparator.compare(sorted.get(i - 1), sorted.get(i)) != 0) {
                rank = i + 1;
            }
            MutableStanding row = sorted.get(i);
            result.add(row.toRow(rank));
        }
        return result;
    }

    public ChampionshipResolution resolveChampionship(
        CompetitionDraftSnapshot snapshot,
        List<CompetitionMatchEntity> matches
    ) {
        List<CompetitionMatchEntity> roundRobin = roundRobinMatches(matches);
        if (!allRoundRobinComplete(roundRobin, snapshot.playerCount())) {
            return ChampionshipResolution.inProgress();
        }

        List<LeagueStandingRow> standings = computeStandings(snapshot, matches);
        List<LeagueStandingRow> leaders = standings.stream().filter(s -> s.rank() == 1).toList();
        return switch (leaders.size()) {
            case 0 -> ChampionshipResolution.inProgress();
            case 1 -> ChampionshipResolution.singleChampion(leaders.get(0).userId());
            case 2 -> playoffFromTiedLeaders(snapshot, leaders);
            default -> ChampionshipResolution.coChampions(
                leaders.stream().map(LeagueStandingRow::userId).toList()
            );
        };
    }

    public static List<CompetitionMatchEntity> roundRobinMatches(List<CompetitionMatchEntity> matches) {
        return matches.stream()
            .filter(m -> !LeagueRules.isPlayoff(m.getRoundKey()))
            .toList();
    }

    public static boolean allRoundRobinComplete(List<CompetitionMatchEntity> roundRobin, int expectedMatches) {
        if (roundRobin.size() < expectedMatches) {
            return false;
        }
        return roundRobin.stream().allMatch(m -> m.getStatus() == CompetitionMatchStatus.COMPLETED);
    }

    private static int expectedRoundRobinMatches(int playerCount) {
        return playerCount * (playerCount - 1) / 2;
    }

    public static boolean isRoundRobinComplete(List<CompetitionMatchEntity> matches, int playerCount) {
        return allRoundRobinComplete(roundRobinMatches(matches), expectedRoundRobinMatches(playerCount));
    }

    private static ChampionshipResolution playoffFromTiedLeaders(
        CompetitionDraftSnapshot snapshot,
        List<LeagueStandingRow> leaders
    ) {
        UUID first = leaders.get(0).userId();
        UUID second = leaders.get(1).userId();
        List<UUID> turnOrder = snapshot.players().stream().map(CompetitionDraftSnapshot.PlayerSnapshot::userId).toList();
        UUID home = turnOrder.indexOf(first) <= turnOrder.indexOf(second) ? first : second;
        UUID away = home.equals(first) ? second : first;
        return ChampionshipResolution.playoffRequired(home, away);
    }

    private void applyMatchResult(Map<UUID, MutableStanding> rows, CompetitionMatchEntity match) {
        int homeScore = match.getHomeScore() != null ? match.getHomeScore() : 0;
        int awayScore = match.getAwayScore() != null ? match.getAwayScore() : 0;
        MutableStanding home = rows.get(match.getHomeUserId());
        MutableStanding away = rows.get(match.getAwayUserId());
        if (home == null || away == null) {
            return;
        }
        home.played++;
        away.played++;
        home.goalsFor += homeScore;
        home.goalsAgainst += awayScore;
        away.goalsFor += awayScore;
        away.goalsAgainst += homeScore;

        switch (matchOutcome(homeScore, awayScore)) {
            case HOME_WIN -> {
                home.won++;
                home.points += WIN_POINTS;
                away.lost++;
            }
            case AWAY_WIN -> {
                away.won++;
                away.points += WIN_POINTS;
                home.lost++;
            }
            case DRAW -> {
                home.drawn++;
                away.drawn++;
                home.points += DRAW_POINTS;
                away.points += DRAW_POINTS;
            }
        }
    }

    private static MatchOutcome matchOutcome(int homeScore, int awayScore) {
        if (homeScore > awayScore) {
            return MatchOutcome.HOME_WIN;
        }
        if (awayScore > homeScore) {
            return MatchOutcome.AWAY_WIN;
        }
        return MatchOutcome.DRAW;
    }

    private Comparator<MutableStanding> buildComparator(List<CompetitionMatchEntity> matches) {
        return Comparator
            .comparingInt((MutableStanding row) -> row.points).reversed()
            .thenComparingInt(row -> row.goalsFor).reversed()
            .thenComparingInt(MutableStanding::goalDifference).reversed()
            .thenComparing((a, b) -> compareHeadToHead(a.userId, b.userId, matches));
    }

    private int compareHeadToHead(UUID a, UUID b, List<CompetitionMatchEntity> matches) {
        CompetitionMatchEntity h2h = findHeadToHead(a, b, matches);
        if (h2h == null || h2h.getStatus() != CompetitionMatchStatus.COMPLETED) {
            return 0;
        }
        int homeScore = h2h.getHomeScore() != null ? h2h.getHomeScore() : 0;
        int awayScore = h2h.getAwayScore() != null ? h2h.getAwayScore() : 0;
        boolean aHome = a.equals(h2h.getHomeUserId());
        int aGoals = aHome ? homeScore : awayScore;
        int bGoals = aHome ? awayScore : homeScore;
        return Integer.compare(bGoals, aGoals);
    }

    private CompetitionMatchEntity findHeadToHead(UUID a, UUID b, List<CompetitionMatchEntity> matches) {
        for (CompetitionMatchEntity match : matches) {
            if (LeagueRules.isPlayoff(match.getRoundKey())) {
                continue;
            }
            if (match.getHomeUserId().equals(a) && match.getAwayUserId().equals(b)) {
                return match;
            }
            if (match.getHomeUserId().equals(b) && match.getAwayUserId().equals(a)) {
                return match;
            }
        }
        return null;
    }

    private enum MatchOutcome {
        HOME_WIN,
        AWAY_WIN,
        DRAW
    }

    private static final class MutableStanding {
        private final UUID userId;
        private final String nickname;
        private int played;
        private int won;
        private int drawn;
        private int lost;
        private int goalsFor;
        private int goalsAgainst;
        private int points;

        private MutableStanding(UUID userId, String nickname) {
            this.userId = userId;
            this.nickname = nickname;
        }

        private int goalDifference() {
            return goalsFor - goalsAgainst;
        }

        private LeagueStandingRow toRow(int rank) {
            return new LeagueStandingRow(
                userId,
                nickname,
                played,
                won,
                drawn,
                lost,
                goalsFor,
                goalsAgainst,
                goalDifference(),
                points,
                rank
            );
        }
    }

    public sealed interface ChampionshipResolution {
        static InProgress inProgress() {
            return InProgress.INSTANCE;
        }

        static SingleChampion singleChampion(UUID userId) {
            return new SingleChampion(userId);
        }

        static CoChampions coChampions(List<UUID> userIds) {
            return new CoChampions(List.copyOf(userIds));
        }

        static PlayoffRequired playoffRequired(UUID homeUserId, UUID awayUserId) {
            return new PlayoffRequired(homeUserId, awayUserId);
        }

        enum InProgress implements ChampionshipResolution {
            INSTANCE
        }

        record SingleChampion(UUID userId) implements ChampionshipResolution {}

        record CoChampions(List<UUID> userIds) implements ChampionshipResolution {}

        record PlayoffRequired(UUID homeUserId, UUID awayUserId) implements ChampionshipResolution {}
    }
}
