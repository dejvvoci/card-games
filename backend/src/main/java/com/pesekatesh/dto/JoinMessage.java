package com.pesekatesh.dto;

public class JoinMessage {
    private String playerId;
    private String username;
    private boolean soloVsBots; // true = Solo Mode (plotëso me bot menjëherë)
    private boolean shtatatEveryRound = true; // false = Shtatat vetëm si raund vendimtar në fund

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public boolean isSoloVsBots() { return soloVsBots; }
    public void setSoloVsBots(boolean soloVsBots) { this.soloVsBots = soloVsBots; }
    public boolean isShtatatEveryRound() { return shtatatEveryRound; }
    public void setShtatatEveryRound(boolean shtatatEveryRound) { this.shtatatEveryRound = shtatatEveryRound; }
}
