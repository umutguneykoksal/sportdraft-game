package com.example.auction.competition.boundary;

import java.util.UUID;

public record LeagueStandingRow(
    UUID userId,
    String nickname,
    int played,
    int won,
    int drawn,
    int lost,
    int goalsFor,
    int goalsAgainst,
    int goalDifference,
    int points,
    int rank
) {}
