package com.pesekatesh.peseqindsh.controller;

import com.pesekatesh.peseqindsh.dto.*;
import com.pesekatesh.peseqindsh.model.*;
import com.pesekatesh.peseqindsh.service.*;
import com.pesekatesh.model.Card;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class PeseqindshController {

    private final PeseqindshRoomManager roomManager;
    private final PeseqindshService gameService;
    private final SimpMessagingTemplate messagingTemplate;

    public PeseqindshController(PeseqindshRoomManager roomManager, PeseqindshService gameService,
                                 SimpMessagingTemplate messagingTemplate) {
        this.roomManager = roomManager;
        this.gameService = gameService;
        this.messagingTemplate = messagingTemplate;
    }

    // ------------------------------------------------------------
    @MessageMapping("/peseqindsh/join/{roomId}")
    public void joinRoom(@DestinationVariable String roomId, @Payload PeseqindshJoinMessage msg) {
        PeseqindshSession session = roomManager.getOrCreateRoom(roomId);
        PeseqindshState state = session.getState();

        if (state.getPlayers().stream().noneMatch(p -> p.getId().equals(msg.getPlayerId())) && !session.isFull()) {
            int seat = state.getPlayers().size();
            state.getPlayers().add(new PeseqindshPlayer(msg.getPlayerId(), msg.getUsername(), seat));
        }
        broadcastState(state);

        if (session.isFull() && state.getPhase() == PeseqindshPhase.WAITING_FOR_PLAYERS) {
            gameService.startNewRound(state, 0); // seat 0 pret raundin e parë
            broadcastState(state);
        }
    }

    // ------------------------------------------------------------
    @MessageMapping("/peseqindsh/openHand/{roomId}")
    public void openHand(@DestinationVariable String roomId, @Payload PeseqindshMoveMessage msg) {
        act(roomId, msg.getPlayerId(), (state, player) -> {
            List<List<Card>> groups = msg.getMeldGroups().stream()
                    .map(g -> g.stream().map(CardDto::toCard).toList())
                    .toList();
            gameService.openHand(state, player, groups);
        });
    }

    @MessageMapping("/peseqindsh/addMeld/{roomId}")
    public void addMeld(@DestinationVariable String roomId, @Payload PeseqindshMoveMessage msg) {
        act(roomId, msg.getPlayerId(), (state, player) -> {
            List<Card> cards = msg.getCards().stream().map(CardDto::toCard).toList();
            gameService.addMeld(state, player, cards);
        });
    }

    @MessageMapping("/peseqindsh/extendMeld/{roomId}")
    public void extendMeld(@DestinationVariable String roomId, @Payload PeseqindshMoveMessage msg) {
        act(roomId, msg.getPlayerId(), (state, player) ->
                gameService.extendMeld(state, player, msg.getMeldId(), msg.getCard().toCard()));
    }

    @MessageMapping("/peseqindsh/discard/{roomId}")
    public void discard(@DestinationVariable String roomId, @Payload PeseqindshMoveMessage msg) {
        act(roomId, msg.getPlayerId(), (state, player) ->
                gameService.discardCard(state, player, msg.getCard().toCard()));
    }

    @MessageMapping("/peseqindsh/drawClosed/{roomId}")
    public void drawClosed(@DestinationVariable String roomId, @Payload PeseqindshMoveMessage msg) {
        act(roomId, msg.getPlayerId(), gameService::drawFromClosed);
    }

    @MessageMapping("/peseqindsh/takeOpenPile/{roomId}")
    public void takeOpenPile(@DestinationVariable String roomId, @Payload PeseqindshMoveMessage msg) {
        act(roomId, msg.getPlayerId(), gameService::takeOpenPile);
    }

    @MessageMapping("/peseqindsh/endForcedTurn/{roomId}")
    public void endForcedTurn(@DestinationVariable String roomId, @Payload PeseqindshMoveMessage msg) {
        act(roomId, msg.getPlayerId(), gameService::endForcedTurn);
    }

    @MessageMapping("/peseqindsh/nextRound/{roomId}")
    public void nextRound(@DestinationVariable String roomId, @Payload PeseqindshMoveMessage msg) {
        PeseqindshSession session = requireRoom(roomId);
        PeseqindshState state = session.getState();
        if (state.getPhase() != PeseqindshPhase.ROUND_FINISHED) return;
        // fituesi i raundit të kaluar = kush ka pikët më të larta të shtuara fundit; e thjeshtë: kush s'ka letra=0
        int winnerSeat = state.getPlayers().stream()
                .max((a, b) -> Integer.compare(a.getTotalScore(), b.getTotalScore()))
                .map(PeseqindshPlayer::getSeatIndex).orElse(0);
        gameService.startNextRound(state, winnerSeat);
        broadcastState(state);
    }

    // ------------------------------------------------------------
    //  HELPERS
    // ------------------------------------------------------------
    @FunctionalInterface
    private interface GameAction {
        void run(PeseqindshState state, PeseqindshPlayer player);
    }

    private void act(String roomId, String playerId, GameAction action) {
        PeseqindshSession session = requireRoom(roomId);
        PeseqindshState state = session.getState();
        PeseqindshPlayer player = state.getPlayers().stream()
                .filter(p -> p.getId().equals(playerId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Lojtari s'u gjet."));
        try {
            action.run(state, player);
        } catch (IllegalStateException ex) {
            messagingTemplate.convertAndSendToUser(playerId, "/queue/peseqindsh-errors", ex.getMessage());
            return;
        }
        broadcastState(state);
    }

    private PeseqindshSession requireRoom(String roomId) {
        PeseqindshSession s = roomManager.getRoom(roomId);
        if (s == null) throw new IllegalStateException("Dhoma nuk ekziston: " + roomId);
        return s;
    }

    private void broadcastState(PeseqindshState state) {
        for (PeseqindshPlayer p : state.getPlayers()) {
            PeseqindshStateDto dto = PeseqindshStateDto.from(state, p.getId());
            messagingTemplate.convertAndSendToUser(p.getId(), "/queue/peseqindsh-state", dto);
        }
        messagingTemplate.convertAndSend("/topic/peseqindsh/room/" + state.getRoomId(),
                PeseqindshStateDto.from(state, null));
    }
}
