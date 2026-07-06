package com.pesekatesh.peseqindsh.dto;

import com.pesekatesh.model.Card;
import com.pesekatesh.peseqindsh.model.*;

import java.util.*;
import java.util.stream.Collectors;

public class PeseqindshStateDto {

    public String roomId;
    public String phase;
    public int currentPlayerSeat;
    public int cutterSeat;
    public int roundNumber;
    public boolean discardedThisTurn;
    public int closedPileCount;
    public List<String> openPile;         // letrat e hapura, të gjitha të dukshme
    public List<CardDto> pendingForcedCards; // vetëm jo-bosh për vizualizuesin (viewer e ka gjithsesi vetë)
    public List<MeldView> melds = new ArrayList<>();
    public List<PlayerView> players = new ArrayList<>();

    /** Epoch ms kur vendi bosh mbushet automatikisht me BOT, nëse dhoma ende pret lojtar */
    public long lobbyDeadlineEpochMs;

    public static class PlayerView {
        public String id;
        public String username;
        public boolean bot;
        public int seatIndex;
        public boolean hasOpened;
        public int totalScore;
        public int cardsInHand;
        public List<String> myHand; // vetëm për "unë"
    }

    public static class MeldView {
        public String id;
        public String type; // SET / RUN
        public int ownerSeat;
        public List<String> cards = new ArrayList<>();
    }

    public static PeseqindshStateDto from(PeseqindshState state, String viewerPlayerId) {
        PeseqindshStateDto dto = new PeseqindshStateDto();
        dto.roomId = state.getRoomId();
        dto.phase = state.getPhase().name();
        dto.currentPlayerSeat = state.getCurrentPlayerSeat();
        dto.cutterSeat = state.getCutterSeat();
        dto.roundNumber = state.getRoundNumber();
        dto.discardedThisTurn = state.isDiscardedThisTurn();
        dto.closedPileCount = state.getClosedPile().size();
        dto.openPile = state.getOpenPile().stream().map(Card::toString).collect(Collectors.toList());
        dto.lobbyDeadlineEpochMs = state.getLobbyDeadlineEpochMs();

        for (Meld m : state.getMelds()) {
            MeldView mv = new MeldView();
            mv.id = m.getId();
            mv.type = m.getType().name();
            mv.ownerSeat = m.getOwnerSeat();
            mv.cards = m.getCards().stream().map(Card::toString).collect(Collectors.toList());
            dto.melds.add(mv);
        }

        for (PeseqindshPlayer p : state.getPlayers()) {
            PlayerView pv = new PlayerView();
            pv.id = p.getId();
            pv.username = p.getUsername();
            pv.bot = p.isBot();
            pv.seatIndex = p.getSeatIndex();
            pv.hasOpened = p.isHasOpened();
            pv.totalScore = p.getTotalScore();
            pv.cardsInHand = p.getHand().size();
            if (p.getId().equals(viewerPlayerId)) {
                pv.myHand = p.getHand().stream().map(Card::toString).collect(Collectors.toList());
            }
            dto.players.add(pv);
        }
        return dto;
    }
}
