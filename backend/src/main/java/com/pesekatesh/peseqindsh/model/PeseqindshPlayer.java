package com.pesekatesh.peseqindsh.model;

import com.pesekatesh.model.Card;

import java.util.ArrayList;
import java.util.List;

public class PeseqindshPlayer {

    private String id;
    private String username;
    private int seatIndex; // 0 ose 1
    private List<Card> hand = new ArrayList<>();
    private boolean hasOpened = false; // a ka arritur kuotën 25p dhe është "shtruar"
    private int totalScore = 0;        // pikët kumulative deri në 500

    public PeseqindshPlayer() {}

    public PeseqindshPlayer(String id, String username, int seatIndex) {
        this.id = id;
        this.username = username;
        this.seatIndex = seatIndex;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public int getSeatIndex() { return seatIndex; }
    public void setSeatIndex(int seatIndex) { this.seatIndex = seatIndex; }
    public List<Card> getHand() { return hand; }
    public void setHand(List<Card> hand) { this.hand = hand; }
    public boolean isHasOpened() { return hasOpened; }
    public void setHasOpened(boolean hasOpened) { this.hasOpened = hasOpened; }
    public int getTotalScore() { return totalScore; }
    public void addScore(int delta) { this.totalScore += delta; }

    public boolean isHandEmpty() { return hand.isEmpty(); }
}
