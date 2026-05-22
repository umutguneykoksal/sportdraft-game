package dev.sportdraft.lobby.boundary;

public record CreateRoomRequest(String nickname, String gameMode, Integer minPoolRating, String poolLeague) {
}
