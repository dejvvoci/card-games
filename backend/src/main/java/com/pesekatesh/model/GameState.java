package com.pesekatesh.model;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameState {

    private String roomId;
    private List<Player> players = new ArrayList<>();   // gjithmonë 4, radhitur sipas seatIndex
    private GamePhase phase = GamePhase.WAITING_FOR_PLAYERS;

    /** Sa kohë (ms) pas hapjes së dhomës, vendet bosh mbushen automatikisht me BOT */
    public static final long LOBBY_BOT_FILL_MS = 60_000;

    /** Momenti (epoch ms) kur dhoma u bë e aksesueshme nga lojtarët; LOBBY_BOT_FILL_MS pas kësaj, vendet bosh mbushen me BOT */
    private long lobbyDeadlineEpochMs = System.currentTimeMillis() + LOBBY_BOT_FILL_MS;
    private final AtomicBoolean lobbyTimerScheduled = new AtomicBoolean(false);

    // ---- KATE 1-4 (trick taking) ----
    private int currentPlayerIndex;
    private Suit ledSuit;                                // shenja e hapur e trick-ut aktual
    private LinkedHashMap<Integer, Card> currentTrick = new LinkedHashMap<>(); // seatIndex -> Card
    /** Trick-et (grupe prej 4 letrash) e fituara nga secili lojtar gjatë raundit aktual — përdoret për pikët e Kate 1-4 dhe për historikun e marrjeve në frontend */
    private Map<Integer, List<List<Card>>> capturedTricks = new HashMap<>();
    /** Pamja e trick-ut të fundit të zgjidhur (mbetet e dukshme edhe pasi currentTrick pastrohet, për animacionin e mbledhjes) */
    private LinkedHashMap<Integer, Card> lastTrickCards = new LinkedHashMap<>();
    private int lastTrickWinnerSeat = -1;
    /** Rritet çdo herë që zgjidhet një trick, që frontend-i të dallojë një zgjidhje të re nga një broadcast tjetër */
    private int trickSeq = 0;

    // ---- KATI 5 (Shtatat) ----
    /** Për çdo shenjë: [low, high] kufijtë aktualë të luajtur; null nëse 7-a ende s'është hapur */
    private Map<Suit, int[]> sevensBounds = new EnumMap<>(Suit.class);
    private List<Integer> finishOrder = new ArrayList<>(); // seatIndex sipas radhës që mbarojnë letrat
    private int blockedPlayerSeat = -1;   // lojtari që është në "pass", pret letër nga i mëparshmi
    private int blockedGiverSeat = -1;    // lojtari që duhet t'i japë letrën (jo domosdoshmërisht seat-i "para" blockedPlayerSeat, nëse ndonjë lojtar tjetër ka mbaruar tashmë)

    // ---- Struktura e ndeshjes (raunde, gati-up, mënyra e Shtatave) ----
    private int roundNumber = 0;
    private int totalRounds = 5;
    private boolean shtatatEveryRound = true; // false = Shtatat vetëm si raund vendimtar në fund, nëse ka barazim
    private boolean tiebreakRound = false;
    private Set<String> readyPlayerIds = new HashSet<>();
    private int matchWinnerSeat = -1;

    // ---- Pikët totale (akumulohen nëpër raunde) ----
    // (mbahen te vetë Player.totalScore)

    public GameState(String roomId) { this.roomId = roomId; }

    // ===== Getters/Setters =====
    public String getRoomId() { return roomId; }
    public List<Player> getPlayers() { return players; }
    public GamePhase getPhase() { return phase; }
    public void setPhase(GamePhase phase) { this.phase = phase; }
    public long getLobbyDeadlineEpochMs() { return lobbyDeadlineEpochMs; }

    /** Rikthen true vetëm herën e parë që thirret (thread-safe) — përdoret për të planifikuar një herë të vetme mbushjen me BOT */
    public boolean markLobbyTimerScheduled() { return lobbyTimerScheduled.compareAndSet(false, true); }
    public int getCurrentPlayerIndex() { return currentPlayerIndex; }
    public void setCurrentPlayerIndex(int i) { this.currentPlayerIndex = i; }
    public Suit getLedSuit() { return ledSuit; }
    public void setLedSuit(Suit ledSuit) { this.ledSuit = ledSuit; }
    public LinkedHashMap<Integer, Card> getCurrentTrick() { return currentTrick; }
    public Map<Integer, List<List<Card>>> getCapturedTricks() { return capturedTricks; }
    public LinkedHashMap<Integer, Card> getLastTrickCards() { return lastTrickCards; }
    public int getLastTrickWinnerSeat() { return lastTrickWinnerSeat; }
    public void setLastTrickWinnerSeat(int s) { this.lastTrickWinnerSeat = s; }
    public int getTrickSeq() { return trickSeq; }
    public void incrementTrickSeq() { this.trickSeq++; }
    public Map<Suit, int[]> getSevensBounds() { return sevensBounds; }
    public List<Integer> getFinishOrder() { return finishOrder; }
    public int getBlockedPlayerSeat() { return blockedPlayerSeat; }
    public void setBlockedPlayerSeat(int s) { this.blockedPlayerSeat = s; }
    public int getBlockedGiverSeat() { return blockedGiverSeat; }
    public void setBlockedGiverSeat(int s) { this.blockedGiverSeat = s; }
    public int getRoundNumber() { return roundNumber; }
    public void setRoundNumber(int roundNumber) { this.roundNumber = roundNumber; }
    public int getTotalRounds() { return totalRounds; }
    public void setTotalRounds(int totalRounds) { this.totalRounds = totalRounds; }
    public boolean isShtatatEveryRound() { return shtatatEveryRound; }
    public void setShtatatEveryRound(boolean v) { this.shtatatEveryRound = v; }
    public boolean isTiebreakRound() { return tiebreakRound; }
    public void setTiebreakRound(boolean v) { this.tiebreakRound = v; }
    public Set<String> getReadyPlayerIds() { return readyPlayerIds; }
    public int getMatchWinnerSeat() { return matchWinnerSeat; }
    public void setMatchWinnerSeat(int s) { this.matchWinnerSeat = s; }

    public Player getPlayerBySeat(int seat) {
        return players.stream().filter(p -> p.getSeatIndex() == seat).findFirst().orElse(null);
    }

    public Player getCurrentPlayer() { return getPlayerBySeat(currentPlayerIndex); }

    public void nextTurn() {
        currentPlayerIndex = (currentPlayerIndex + 1) % 4;
    }

    public void resetForNewRound() {
        currentTrick.clear();
        capturedTricks.clear();
        sevensBounds.clear();
        finishOrder.clear();
        blockedPlayerSeat = -1;
        blockedGiverSeat = -1;
        ledSuit = null;
        lastTrickCards.clear();
        lastTrickWinnerSeat = -1;
        readyPlayerIds.clear();
        for (Player p : players) {
            capturedTricks.put(p.getSeatIndex(), new ArrayList<>());
            p.setFinishPosition(0);
        }
    }
}
