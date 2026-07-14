package com.pesekatesh.derr;

public class DerrJoinMessage {
    private String playerId;
    private String username;

    /** true = Solo Mode (plotëso menjëherë vendet e tjera me BOT, pa pritur 60s) */
    private boolean soloVsBots;

    /** Token i llogarisë (opsional) — nëse lojtari është i loguar, lidh Player-in me User-in për historikun e statistikave */
    private String authToken;

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public boolean isSoloVsBots() { return soloVsBots; }
    public void setSoloVsBots(boolean soloVsBots) { this.soloVsBots = soloVsBots; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
}
