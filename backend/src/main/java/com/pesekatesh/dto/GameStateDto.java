package com.pesekatesh.dto;

import com.pesekatesh.model.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * View e personalizuar e GameState për një lojtar specifik:
 * - shikon vetëm dorën e vet të plotë
 * - për të tjerët shikon vetëm numrin e letrave që kanë në dorë
 */
public class GameStateDto {

    public String roomId;
    public String phase;
    public int currentPlayerSeat;
    public String ledSuit;
    public Map<Integer, String> currentTrick = new LinkedHashMap<>();
    public List<PlayerView> players = new ArrayList<>();
    public Map<String, int[]> sevensBounds = new HashMap<>(); // suit -> [low, high]
    public int blockedPlayerSeat;
    public int blockedGiverSeat;
    public List<Integer> finishOrder;
    /** Pamja e trick-ut të fundit të zgjidhur, për animacionin e mbledhjes te frontend */
    public Map<Integer, String> lastTrick = new LinkedHashMap<>();
    public int lastTrickWinnerSeat;
    public int trickSeq;

    // ---- Struktura e ndeshjes ----
    public int roundNumber;
    public int totalRounds;
    public boolean shtatatEveryRound;
    public boolean tiebreakRound;
    public List<Integer> readySeats = new ArrayList<>();
    public int matchWinnerSeat;

    /** Epoch ms kur vendet bosh mbushen automatikisht me BOT, nëse dhoma ende pret lojtarë */
    public long lobbyDeadlineEpochMs;

    public static class PlayerView {
        public String id;
        public String username;
        public boolean bot;
        public int seatIndex;
        public int totalScore;
        public int cardsInHand;
        public List<String> myHand; // null nëse s'je ti
        /** Trick-et (grupe prej 4 letrash) e marra gjatë raundit aktual — vetëm për vetë lojtarin */
        public List<List<String>> myCapturedTricks; // null nëse s'je ti

        public PlayerView() {}
    }

    public static GameStateDto from(GameState state, String viewerPlayerId) {
        GameStateDto dto = new GameStateDto();
        dto.roomId = state.getRoomId();
        dto.phase = state.getPhase().name();
        dto.currentPlayerSeat = state.getCurrentPlayerIndex();
        dto.ledSuit = state.getLedSuit() != null ? state.getLedSuit().name() : null;
        dto.blockedPlayerSeat = state.getBlockedPlayerSeat();
        dto.blockedGiverSeat = state.getBlockedGiverSeat();
        dto.finishOrder = state.getFinishOrder();

        for (Map.Entry<Integer, Card> e : state.getCurrentTrick().entrySet()) {
            dto.currentTrick.put(e.getKey(), e.getValue().toString());
        }
        for (Map.Entry<Integer, Card> e : state.getLastTrickCards().entrySet()) {
            dto.lastTrick.put(e.getKey(), e.getValue().toString());
        }
        dto.lastTrickWinnerSeat = state.getLastTrickWinnerSeat();
        dto.trickSeq = state.getTrickSeq();

        dto.roundNumber = state.getRoundNumber();
        dto.totalRounds = state.getTotalRounds();
        dto.shtatatEveryRound = state.isShtatatEveryRound();
        dto.tiebreakRound = state.isTiebreakRound();
        dto.matchWinnerSeat = state.getMatchWinnerSeat();
        dto.lobbyDeadlineEpochMs = state.getLobbyDeadlineEpochMs();
        for (Player p : state.getPlayers()) {
            if (state.getReadyPlayerIds().contains(p.getId())) {
                dto.readySeats.add(p.getSeatIndex());
            }
        }

        for (Suit s : Suit.values()) {
            int[] bounds = state.getSevensBounds().get(s);
            if (bounds != null) dto.sevensBounds.put(s.name(), bounds);
        }

        for (Player p : state.getPlayers()) {
            PlayerView pv = new PlayerView();
            pv.id = p.getId();
            pv.username = p.getUsername();
            pv.bot = p.isBot();
            pv.seatIndex = p.getSeatIndex();
            pv.totalScore = p.getTotalScore();
            pv.cardsInHand = p.getHand().size();
            if (p.getId().equals(viewerPlayerId)) {
                pv.myHand = p.getHand().stream().map(Card::toString).collect(Collectors.toList());
                List<List<Card>> tricks = state.getCapturedTricks().getOrDefault(p.getSeatIndex(), List.of());
                pv.myCapturedTricks = tricks.stream()
                        .map(trick -> trick.stream().map(Card::toString).collect(Collectors.toList()))
                        .collect(Collectors.toList());
            }
            dto.players.add(pv);
        }
        return dto;
    }
}
