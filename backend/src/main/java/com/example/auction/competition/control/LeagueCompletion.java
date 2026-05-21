package com.example.auction.competition.control;

import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionStatus;
import com.example.auction.competition.entity.LeaguePhase;
import java.util.List;
import java.util.UUID;

/** Shared league completion helpers for single champion, co-champions, or play-off. */
final class LeagueCompletion {

    private LeagueCompletion() {}

    static void markSingleChampion(CompetitionEntity competition, UUID championUserId) {
        competition.setChampionUserId(championUserId);
        competition.setCoChampionUserIdsJson(null);
        markFinished(competition);
    }

    static void markCoChampions(CompetitionEntity competition, CompetitionJson competitionJson, List<UUID> userIds) {
        competition.setCoChampionUserIdsJson(competitionJson.writeUuidList(userIds));
        competition.setChampionUserId(null);
        markFinished(competition);
    }

    static void markFinished(CompetitionEntity competition) {
        competition.setStatus(CompetitionStatus.COMPLETED);
        competition.setLeaguePhase(LeaguePhase.REPLAYS);
        competition.setIntermissionEndsAt(null);
    }
}
