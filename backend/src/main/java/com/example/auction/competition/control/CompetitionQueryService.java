package com.example.auction.competition.control;

import com.example.auction.competition.boundary.CompetitionMatchResponse;
import com.example.auction.competition.boundary.CompetitionResponse;
import com.example.auction.competition.entity.CompetitionEntity;
import com.example.auction.competition.entity.CompetitionMatchEntity;
import com.example.auction.competition.entity.CompetitionMatchRepository;
import com.example.auction.competition.entity.CompetitionRepository;
import com.example.auction.session.redis.ActivePlayer;
import com.example.auction.session.redis.ActiveSession;
import com.example.auction.session.redis.ActiveSessionStore;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompetitionQueryService {

    private final CompetitionRepository competitionRepository;
    private final CompetitionMatchRepository matchRepository;
    private final ActiveSessionStore sessionStore;

    public CompetitionQueryService(
        CompetitionRepository competitionRepository,
        CompetitionMatchRepository matchRepository,
        ActiveSessionStore sessionStore
    ) {
        this.competitionRepository = competitionRepository;
        this.matchRepository = matchRepository;
        this.sessionStore = sessionStore;
    }

    public CompetitionResponse getBySessionId(UUID sessionId) {
        CompetitionEntity competition = competitionRepository.findBySessionId(sessionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Competition not found"));

        ActiveSession session = sessionStore.findById(sessionId).orElse(null);
        List<CompetitionMatchEntity> matches = matchRepository.findByCompetitionIdOrderBySortOrderAsc(competition.getId());

        List<CompetitionMatchResponse> matchResponses = matches.stream()
            .map(m -> new CompetitionMatchResponse(
                m.getId(),
                m.getRoundKey(),
                m.getHomeUserId(),
                nickname(session, m.getHomeUserId()),
                m.getAwayUserId(),
                nickname(session, m.getAwayUserId()),
                m.getHomeScore(),
                m.getAwayScore(),
                m.getStatus().name()
            ))
            .toList();

        return new CompetitionResponse(
            competition.getId(),
            competition.getSessionId(),
            competition.getType().name(),
            competition.getStatus().name(),
            competition.getChampionUserId(),
            championNickname(session, competition.getChampionUserId()),
            matchResponses
        );
    }

    private String nickname(ActiveSession session, UUID userId) {
        if (session == null || userId == null) {
            return null;
        }
        return session.findPlayerByUserId(userId).map(ActivePlayer::getNickname).orElse(null);
    }

    private String championNickname(ActiveSession session, UUID championUserId) {
        return nickname(session, championUserId);
    }
}
