package com.pesekatesh.peseqindsh.controller;

import com.pesekatesh.peseqindsh.dto.*;
import com.pesekatesh.peseqindsh.model.*;
import com.pesekatesh.peseqindsh.service.*;
import com.pesekatesh.model.Card;
import com.pesekatesh.stats.GameResultService;
import com.pesekatesh.user.AuthTokenService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Controller
public class PeseqindshController {

    private final PeseqindshRoomManager roomManager;
    private final PeseqindshService gameService;
    private final PeseqindshBotService botService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AuthTokenService authTokenService;
    private final GameResultService gameResultService;
    private final ScheduledExecutorService botScheduler = Executors.newSingleThreadScheduledExecutor();

    public PeseqindshController(PeseqindshRoomManager roomManager, PeseqindshService gameService,
                                 PeseqindshBotService botService, SimpMessagingTemplate messagingTemplate,
                                 AuthTokenService authTokenService, GameResultService gameResultService) {
        this.roomManager = roomManager;
        this.gameService = gameService;
        this.botService = botService;
        this.messagingTemplate = messagingTemplate;
        this.authTokenService = authTokenService;
        this.gameResultService = gameResultService;
    }

    // ------------------------------------------------------------
    @MessageMapping("/peseqindsh/join/{roomId}")
    public void joinRoom(@DestinationVariable String roomId, @Payload PeseqindshJoinMessage msg) {
        PeseqindshSession session = roomManager.getOrCreateRoom(roomId);
        PeseqindshState state = session.getState();

        if (state.getPlayers().stream().noneMatch(p -> p.getId().equals(msg.getPlayerId())) && !session.isFull()) {
            int seat = state.getPlayers().size();
            PeseqindshPlayer player = new PeseqindshPlayer(msg.getPlayerId(), msg.getUsername(), seat);
            player.setUserId(authTokenService.resolveUserId(msg.getAuthToken()).orElse(null));
            state.getPlayers().add(player);
        }

        if (msg.isSoloVsBots()) {
            // SOLO MODE: plotëso menjëherë vendin tjetër me BOT
            fillRemainingSeatsWithBots(state);
        } else {
            // MULTIPLAYER: nëse brenda 60s nga hapja e dhomës vendi tjetër s'plotësohet me lojtar të vërtetë,
            // plotësohet automatikisht me BOT dhe loja fillon
            scheduleLobbyBotFillIfNeeded(session);
        }

        broadcastState(state);
        startMatchIfReady(session);
    }

    /** Plotëson vendin bosh të mbetur me BOT (deri në 2 lojtarë) */
    private void fillRemainingSeatsWithBots(PeseqindshState state) {
        while (state.getPlayers().size() < 2) {
            int seat = state.getPlayers().size();
            state.getPlayers().add(new PeseqindshPlayer("bot-" + UUID.randomUUID(), "Bot " + seat, seat, true));
        }
    }

    /** Kur dhoma është plot (2 lojtarë, real ose BOT), fillo lojën nëse ende s'ka filluar */
    private void startMatchIfReady(PeseqindshSession session) {
        PeseqindshState state = session.getState();
        if (session.isFull() && state.getPhase() == PeseqindshPhase.WAITING_FOR_PLAYERS) {
            gameService.startNewRound(state, 0); // seat 0 pret raundin e parë
            broadcastState(state);
            triggerBotTurnIfNeeded(session);
        }
    }

    /** Planifikon, vetëm një herë për këtë dhomë, mbushjen me BOT pas PeseqindshState.LOBBY_BOT_FILL_MS */
    private void scheduleLobbyBotFillIfNeeded(PeseqindshSession session) {
        PeseqindshState state = session.getState();
        if (!state.markLobbyTimerScheduled()) return; // tashmë e planifikuar

        long delayMs = Math.max(0, state.getLobbyDeadlineEpochMs() - System.currentTimeMillis());
        botScheduler.schedule(() -> {
            if (state.getPhase() != PeseqindshPhase.WAITING_FOR_PLAYERS) return; // loja tashmë filloi vetë
            fillRemainingSeatsWithBots(state);
            broadcastState(state);
            startMatchIfReady(session);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /** Nëse është radha e një BOT-i, luan automatikisht pas një vonese të shkurtër */
    private void triggerBotTurnIfNeeded(PeseqindshSession session) {
        PeseqindshState state = session.getState();
        if (state.getPhase() != PeseqindshPhase.PLAYING) return;
        PeseqindshPlayer current = state.getCurrentPlayer();
        if (current == null || !current.isBot()) return;

        botScheduler.schedule(() -> {
            try {
                botService.playTurn(state, current, gameService);
            } catch (IllegalStateException ignored) { /* rast tjetërsor, humbi race */ }
            broadcastState(state);
            triggerBotTurnIfNeeded(session); // vazhdon nëse edhe radha tjetër është BOT
        }, 900, TimeUnit.MILLISECONDS);
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

    @MessageMapping("/peseqindsh/takeFromOpenPile/{roomId}")
    public void takeFromOpenPile(@DestinationVariable String roomId, @Payload PeseqindshMoveMessage msg) {
        act(roomId, msg.getPlayerId(), (state, player) ->
                gameService.takeFromOpenPile(state, player, msg.getCard().toCard()));
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
        triggerBotTurnIfNeeded(session);
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
        triggerBotTurnIfNeeded(session);
    }

    private PeseqindshSession requireRoom(String roomId) {
        PeseqindshSession s = roomManager.getRoom(roomId);
        if (s == null) throw new IllegalStateException("Dhoma nuk ekziston: " + roomId);
        return s;
    }

    private void broadcastState(PeseqindshState state) {
        for (PeseqindshPlayer p : state.getPlayers()) {
            PeseqindshStateDto dto = PeseqindshStateDto.from(state, p.getId(), gameService);
            messagingTemplate.convertAndSendToUser(p.getId(), "/queue/peseqindsh-state", dto);
        }
        messagingTemplate.convertAndSend("/topic/peseqindsh/room/" + state.getRoomId(),
                PeseqindshStateDto.from(state, null, gameService));

        // Ndeshja sapo mbaroi -> regjistro historikun e statistikave (vetëm një herë) për lojtarët e loguar
        if (state.getPhase() == PeseqindshPhase.GAME_OVER && state.markResultsRecorded()) {
            gameResultService.recordPeseqindshResult(state);
        }
    }
}
