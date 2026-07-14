package com.pesekatesh.derr;

import com.pesekatesh.model.Card;

import java.util.*;
import java.util.stream.Collectors;

/**
 * View e personalizuar e DerrState për një lojtar specifik: shikon vetëm dorën e vet të plotë,
 * për të tjerët shikon vetëm sa letra kanë (për t'i shfaqur si letra të kthyera që mund të klikohen).
 */
public class DerrStateDto {

    public String roomId;
    public String phase;
    public int holderSeat;
    public int drawerSeat;
    public int lastEscapedSeat;
    public int escapeSeq;
    public int matchLoserSeat;
    public List<String> burnedPairs = new ArrayList<>();
    public List<PlayerView> players = new ArrayList<>();
    public long lobbyDeadlineEpochMs;

    public static class PlayerView {
        public String id;
        public String username;
        public boolean bot;
        public int seatIndex;
        public boolean active;
        public int cardsInHand;
        public List<String> myHand; // vetëm për "unë"
    }

    public static DerrStateDto from(DerrState state, String viewerPlayerId) {
        DerrStateDto dto = new DerrStateDto();
        dto.roomId = state.getRoomId();
        dto.phase = state.getPhase().name();
        dto.holderSeat = state.getHolderSeat();
        dto.drawerSeat = state.getDrawerSeat();
        dto.lastEscapedSeat = state.getLastEscapedSeat();
        dto.escapeSeq = state.getEscapeSeq();
        dto.matchLoserSeat = state.getMatchLoserSeat();
        dto.burnedPairs = state.getBurnedPairs().stream().map(Card::toString).collect(Collectors.toList());
        dto.lobbyDeadlineEpochMs = state.getLobbyDeadlineEpochMs();

        for (DerrPlayer p : state.getPlayers()) {
            PlayerView pv = new PlayerView();
            pv.id = p.getId();
            pv.username = p.getUsername();
            pv.bot = p.isBot();
            pv.seatIndex = p.getSeatIndex();
            pv.active = p.isActive();
            pv.cardsInHand = p.getHand().size();
            if (p.getId().equals(viewerPlayerId)) {
                pv.myHand = p.getHand().stream().map(Card::toString).collect(Collectors.toList());
            }
            dto.players.add(pv);
        }
        return dto;
    }
}
