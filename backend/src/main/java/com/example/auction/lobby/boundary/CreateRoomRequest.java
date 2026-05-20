package com.example.auction.lobby.boundary;

public record CreateRoomRequest(String nickname, String gameMode, Integer minPoolRating, String poolLeague) {
}
