package com.pesekatesh.model;

import java.util.ArrayList;
import java.util.List;

public class Player {

    private String id;          // sessionId ose UUID
    private String username;
    private boolean bot;
    private int seatIndex;      // 0..3, radha në tavolinë
    private List<Card> hand = new ArrayList<>();
    private int totalScore = 0;

    /** Lidhja me llogarinë e loguar (nëse ka) — përdoret vetëm për të regjistruar historikun e statistikave */
    private Long userId;

    /** Për Katin e 5-të: pozicioni kur mbaron letrat (1=i pari, 4=i fundit) */
    private int finishPosition = 0;

    public Player() {}

    public Player(String id, String username, boolean bot, int seatIndex) {
        this.id = id;
        this.username = username;
        this.bot = bot;
        this.seatIndex = seatIndex;
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public boolean isBot() { return bot; }
    public void setBot(boolean bot) { this.bot = bot; }
    public int getSeatIndex() { return seatIndex; }
    public void setSeatIndex(int seatIndex) { this.seatIndex = seatIndex; }
    public List<Card> getHand() { return hand; }
    public void setHand(List<Card> hand) { this.hand = hand; }
    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }
    public void addScore(int delta) { this.totalScore += delta; }
    public int getFinishPosition() { return finishPosition; }
    public void setFinishPosition(int finishPosition) { this.finishPosition = finishPosition; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public boolean hasSuit(Suit suit) {
        return hand.stream().anyMatch(c -> c.getSuit() == suit);
    }

    public boolean isHandEmpty() { return hand.isEmpty(); }
}
