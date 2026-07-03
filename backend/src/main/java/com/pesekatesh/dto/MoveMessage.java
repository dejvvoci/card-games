package com.pesekatesh.dto;

public class MoveMessage {
    private String playerId;
    private String suit;   // "MAC","ZEMER","ROMB","SPATHI"
    private int rank;      // 2..14
    private String targetPlayerId; // përdoret vetëm te tradeCard (transferimi te bllokimi)

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getSuit() { return suit; }
    public void setSuit(String suit) { this.suit = suit; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }
    public String getTargetPlayerId() { return targetPlayerId; }
    public void setTargetPlayerId(String targetPlayerId) { this.targetPlayerId = targetPlayerId; }
}
