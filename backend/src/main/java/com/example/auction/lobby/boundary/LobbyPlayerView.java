package com.example.auction.lobby.boundary;

import java.util.UUID;

public record LobbyPlayerView(UUID userId, String nickname, int seatIndex) {
}
