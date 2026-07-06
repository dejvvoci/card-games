package com.pesekatesh.peseqindsh.dto;

public class PeseqindshJoinMessage {
    private String playerId;
    private String username;

    /** Token i llogarisë (opsional) — nëse lojtari është i loguar, lidh Player-in me User-in për historikun e statistikave */
    private String authToken;

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
}
