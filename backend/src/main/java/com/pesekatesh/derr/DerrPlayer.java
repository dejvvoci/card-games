package com.pesekatesh.derr;

import com.pesekatesh.model.Card;

import java.util.ArrayList;
import java.util.List;

public class DerrPlayer {

    private String id;
    private String username;
    private boolean bot;
    private int seatIndex;
    private List<Card> hand = new ArrayList<>();

    /** false kur lojtarit i kanë mbaruar letrat (ka "shpëtuar") — del nga rrotullimi i lojës */
    private boolean active = true;

    /** Lidhja me llogarinë e loguar (nëse ka) — përdoret vetëm për të regjistruar historikun e statistikave */
    private Long userId;

    public DerrPlayer() {}

    public DerrPlayer(String id, String username, int seatIndex, boolean bot) {
        this.id = id;
        this.username = username;
        this.seatIndex = seatIndex;
        this.bot = bot;
    }

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
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public boolean isHandEmpty() { return hand.isEmpty(); }
}
