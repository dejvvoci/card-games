package com.pesekatesh.derr;

import com.pesekatesh.model.Card;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DerrState {

    /** Numri fiks i vendeve në tavolinë (si te Pesëkatësh) */
    public static final int SEAT_COUNT = 4;

    /** Sa kohë (ms) pas hapjes së dhomës, vendet bosh mbushen automatikisht me BOT */
    public static final long LOBBY_BOT_FILL_MS = 60_000;

    private String roomId;
    private List<DerrPlayer> players = new ArrayList<>();
    private DerrPhase phase = DerrPhase.WAITING_FOR_PLAYERS;

    /** Vendi që "mban" dorën e shtrirë (i verbër) për t'ia dhënë lojtarit tjetër */
    private int holderSeat;
    /** Vendi që ka radhën të tërheqë (të vjedhë) një letër nga holderSeat */
    private int drawerSeat;

    /** Rendi (sipas vendit) me të cilin lojtarët "shpëtuan" — i pari të dalë = më i sigurti */
    private final List<Integer> finishOrder = new ArrayList<>();

    /** Letrat e djegura (çiftet e hedhura), vetëm për shfaqje vizuale */
    private final List<Card> burnedPairs = new ArrayList<>();

    /** Vendi i lojtarit që sapo "shpëtoi" në veprimin e fundit — për njoftimin "Shpëtoi!" në frontend */
    private int lastEscapedSeat = -1;
    /** Rritet çdo herë që dikush shpëton, që frontend-i të dallojë një ngjarje të re nga një broadcast tjetër */
    private int escapeSeq = 0;

    private int matchLoserSeat = -1; // "Derri"

    private long lobbyDeadlineEpochMs = System.currentTimeMillis() + LOBBY_BOT_FILL_MS;
    private final AtomicBoolean lobbyTimerScheduled = new AtomicBoolean(false);
    private final AtomicBoolean resultsRecorded = new AtomicBoolean(false);

    public DerrState(String roomId) { this.roomId = roomId; }

    // ===== Getters/Setters =====
    public String getRoomId() { return roomId; }
    public List<DerrPlayer> getPlayers() { return players; }
    public DerrPhase getPhase() { return phase; }
    public void setPhase(DerrPhase phase) { this.phase = phase; }
    public int getHolderSeat() { return holderSeat; }
    public void setHolderSeat(int s) { this.holderSeat = s; }
    public int getDrawerSeat() { return drawerSeat; }
    public void setDrawerSeat(int s) { this.drawerSeat = s; }
    public List<Integer> getFinishOrder() { return finishOrder; }
    public List<Card> getBurnedPairs() { return burnedPairs; }
    public int getLastEscapedSeat() { return lastEscapedSeat; }
    public void setLastEscapedSeat(int s) { this.lastEscapedSeat = s; }
    public int getEscapeSeq() { return escapeSeq; }
    public void incrementEscapeSeq() { this.escapeSeq++; }
    public int getMatchLoserSeat() { return matchLoserSeat; }
    public void setMatchLoserSeat(int s) { this.matchLoserSeat = s; }
    public long getLobbyDeadlineEpochMs() { return lobbyDeadlineEpochMs; }

    /** Rikthen true vetëm herën e parë që thirret (thread-safe) — përdoret për të planifikuar një herë të vetme mbushjen me BOT */
    public boolean markLobbyTimerScheduled() { return lobbyTimerScheduled.compareAndSet(false, true); }

    /** Rikthen true vetëm herën e parë që thirret (thread-safe) — siguron që rezultatet regjistrohen një herë të vetme në DB */
    public boolean markResultsRecorded() { return resultsRecorded.compareAndSet(false, true); }

    public DerrPlayer getPlayerBySeat(int seat) {
        return players.stream().filter(p -> p.getSeatIndex() == seat).findFirst().orElse(null);
    }

    public DerrPlayer getHolder() { return getPlayerBySeat(holderSeat); }
    public DerrPlayer getDrawer() { return getPlayerBySeat(drawerSeat); }

    /** Vendet ende aktive (s'kanë shpëtuar ende) */
    public List<Integer> activeSeats() {
        return players.stream().filter(DerrPlayer::isActive).map(DerrPlayer::getSeatIndex).sorted().toList();
    }

    /** Vendi tjetër aktiv në drejtim orar, duke filluar nga (from+1) — mund të kthejë 'from' vetë nëse është i vetmi aktiv */
    public int nextActiveSeat(int from) {
        for (int i = 1; i <= SEAT_COUNT; i++) {
            int candidate = (from + i) % SEAT_COUNT;
            DerrPlayer p = getPlayerBySeat(candidate);
            if (p != null && p.isActive()) return candidate;
        }
        return from;
    }

    /** Vendi aktiv paraardhës (drejtim anti-orar), duke filluar nga (from-1) */
    public int previousActiveSeat(int from) {
        for (int i = 1; i <= SEAT_COUNT; i++) {
            int candidate = (from - i + SEAT_COUNT) % SEAT_COUNT;
            DerrPlayer p = getPlayerBySeat(candidate);
            if (p != null && p.isActive()) return candidate;
        }
        return from;
    }
}
