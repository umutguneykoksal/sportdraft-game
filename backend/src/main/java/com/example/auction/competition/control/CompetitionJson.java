package com.example.auction.competition.control;

import com.example.auction.simulation.entity.MatchEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CompetitionJson {

    private final ObjectMapper objectMapper;

    public CompetitionJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String writeSnapshot(TournamentDraftSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize tournament snapshot", e);
        }
    }

    public TournamentDraftSnapshot readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, TournamentDraftSnapshot.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize tournament snapshot", e);
        }
    }

    public String writeTimeline(List<MatchEvent> timeline) {
        try {
            return objectMapper.writeValueAsString(timeline);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize match timeline", e);
        }
    }

    public List<MatchEvent> readTimeline(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize match timeline", e);
        }
    }
}
