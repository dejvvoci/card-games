package com.pesekatesh.derr;

public class DerrMoveMessage {
    private String playerId;
    /** Pozicioni (0-indeksuar) i letrës së zgjedhur nga dora e verbër e holder-it */
    private int cardIndex;

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public int getCardIndex() { return cardIndex; }
    public void setCardIndex(int cardIndex) { this.cardIndex = cardIndex; }
}
