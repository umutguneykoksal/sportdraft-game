package com.example.auction.lobby.boundary;

import java.util.List;

public record PoolMinRatingOptionsResponse(String poolLeague, List<PoolMinRatingOptionView> options) {}
