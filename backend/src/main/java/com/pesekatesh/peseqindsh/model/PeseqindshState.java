package com.pesekatesh.peseqindsh.model;

import com.pesekatesh.model.Card;

import java.util.*;

public class PeseqindshState {

    public static final int TARGET_SCORE = 500;
    public static final int OPENING_THRESHOLD = 25;

    private String roomId;
    private List<PeseqindshPlayer> players = new ArrayList<>(); // gjithmonë 2
    private PeseqindshPhase phase = PeseqindshPhase.WAITING_FOR_PLAYERS;

    private Deque<Card> closedPile = new ArrayDeque<>();  // Talon (grumbulli i mbyllur)
    private List<Card> openPile = new ArrayList<>();       // letrat e hapura/prera (fundi i listës = maja)
    private List<Meld> melds = new ArrayList<>();          // kombinimet e shtruara nga të dy lojtarët

    private int currentPlayerSeat;
    private int cutterSeat = 0; // kush ka të drejtë të presë letrat për raundin aktual

    /**
     * Kur një lojtar merr të GJITHA letrat e hapura (rregulli special), këto letra duhet
     * të "përdoren menjëherë" në kombinime para se radha t'i kalojë kundërshtarit.
     * I mbajmë këtu për t'i detyruar server-side.
     */
    private List<Card> pendingForcedCards = new ArrayList<>();

    /** A ka bërë lojtari aktual hedhjen e detyrueshme të letrës këtë radhë? */
    private boolean discardedThisTurn = false;

    private int roundNumber = 1;

    public PeseqindshState(String roomId) { this.roomId = roomId; }

    // ===== Getters/Setters =====
    public String getRoomId() { return roomId; }
    public List<PeseqindshPlayer> getPlayers() { return players; }
    public PeseqindshPhase getPhase() { return phase; }
    public void setPhase(PeseqindshPhase phase) { this.phase = phase; }
    public Deque<Card> getClosedPile() { return closedPile; }
    public List<Card> getOpenPile() { return openPile; }
    public List<Meld> getMelds() { return melds; }
    public int getCurrentPlayerSeat() { return currentPlayerSeat; }
    public void setCurrentPlayerSeat(int s) { this.currentPlayerSeat = s; }
    public int getCutterSeat() { return cutterSeat; }
    public void setCutterSeat(int s) { this.cutterSeat = s; }
    public List<Card> getPendingForcedCards() { return pendingForcedCards; }
    public boolean isDiscardedThisTurn() { return discardedThisTurn; }
    public void setDiscardedThisTurn(boolean v) { this.discardedThisTurn = v; }
    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int r) { this.roundNumber = r; }

    public PeseqindshPlayer getPlayerBySeat(int seat) {
        return players.stream().filter(p -> p.getSeatIndex() == seat).findFirst().orElse(null);
    }

    public PeseqindshPlayer getCurrentPlayer() { return getPlayerBySeat(currentPlayerSeat); }

    public PeseqindshPlayer getOpponentOf(int seat) {
        return players.stream().filter(p -> p.getSeatIndex() != seat).findFirst().orElse(null);
    }

    public void switchTurn() {
        currentPlayerSeat = (currentPlayerSeat + 1) % 2;
        discardedThisTurn = false;
    }

    public List<Meld> meldsOwnedBy(int seat) {
        return melds.stream().filter(m -> m.getOwnerSeat() == seat).toList();
    }

    public void resetForNewRound() {
        closedPile.clear();
        openPile.clear();
        melds.clear();
        pendingForcedCards.clear();
        discardedThisTurn = false;
        for (PeseqindshPlayer p : players) {
            p.getHand().clear();
            p.setHasOpened(false);
        }
    }
}
