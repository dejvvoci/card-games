package com.pesekatesh.controller;

import com.pesekatesh.dto.*;
import com.pesekatesh.model.*;
import com.pesekatesh.service.*;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Controller
public class GameController {

    private final RoomManager roomManager;
    private final GameService gameService;
    private final BotService botService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ScheduledExecutorService botScheduler = Executors.newSingleThreadScheduledExecutor();

    public GameController(RoomManager roomManager, GameService gameService,
                           BotService botService, SimpMessagingTemplate messagingTemplate) {
        this.roomManager = roomManager;
        this.gameService = gameService;
        this.botService = botService;
        this.messagingTemplate = messagingTemplate;
    }

    // ============================================================
    //  1) JOIN ROOM  (Multiplayer real ose Solo me bot-e)
    // ============================================================
    @MessageMapping("/join/{roomId}")
    public void joinRoom(@DestinationVariable String roomId, @Payload JoinMessage msg) {
        GameSession session = roomManager.getOrCreateRoom(roomId, msg.isSoloVsBots(), msg.isShtatatEveryRound());
        GameState state = session.getState();

        if (state.getPlayers().stream().noneMatch(p -> p.getId().equals(msg.getPlayerId())) && !session.isFull()) {
            int seat = state.getPlayers().size();
            state.getPlayers().add(new Player(msg.getPlayerId(), msg.getUsername(), false, seat));
        }

        // SOLO MODE: plotëso menjëherë me 3 bot-e
        if (msg.isSoloVsBots()) {
            while (state.getPlayers().size() < 4) {
                int seat = state.getPlayers().size();
                state.getPlayers().add(new Player("bot-" + UUID.randomUUID(), "Bot " + seat, true, seat));
            }
        }

        broadcastState(state);

        // Kur dhoma është plot (4 lojtarë), fillo lojën
        if (session.isFull() && state.getPhase() == GamePhase.WAITING_FOR_PLAYERS) {
            state.setRoundNumber(1);
            gameService.dealNewRound(state, 0);
            broadcastState(state);
            triggerBotTurnIfNeeded(session);
        }
    }

    // ============================================================
    //  5) READY (gati për raundin tjetër, pas ROUND_FINISHED)
    // ============================================================
    @MessageMapping("/ready/{roomId}")
    public void markReady(@DestinationVariable String roomId, @Payload MoveMessage msg) {
        GameSession session = requireRoom(roomId);
        GameState state = session.getState();
        if (state.getPhase() != GamePhase.ROUND_FINISHED) return;

        state.getReadyPlayerIds().add(msg.getPlayerId());
        broadcastState(state);

        boolean allHumansReady = state.getPlayers().stream()
                .filter(p -> !p.isBot())
                .allMatch(p -> state.getReadyPlayerIds().contains(p.getId()));
        if (!allHumansReady) return;

        gameService.advanceToNextRoundOrFinish(state);
        broadcastState(state);
        triggerBotTurnIfNeeded(session); // raundi/tiebreak-u i ri mund të fillojë me radhë të një bot-i
    }

    // ============================================================
    //  2) PLAY CARD  (Kate 1-4 ose Kati 5 - Shtatat)
    // ============================================================
    @MessageMapping("/play/{roomId}")
    public void playCard(@DestinationVariable String roomId, @Payload MoveMessage msg) {
        GameSession session = requireRoom(roomId);
        GameState state = session.getState();
        Player player = findPlayer(state, msg.getPlayerId());
        Card card = new Card(Suit.valueOf(msg.getSuit()), msg.getRank());

        try {
            if (state.getPhase() == GamePhase.KATE_1_4) {
                gameService.playCardKate1to4(state, player, card);
                if (state.getPhase() == GamePhase.KATI_5_SHTATAT
                        && state.getSevensBounds().isEmpty()
                        && state.getFinishOrder().isEmpty()) {
                    gameService.startKati5(state); // sapo mbaroi kate 1-4, hap shtatat
                }
            } else if (state.getPhase() == GamePhase.KATI_5_SHTATAT) {
                gameService.playCardKati5(state, player, card);
            }
        } catch (IllegalStateException ex) {
            sendPrivateError(msg.getPlayerId(), ex.getMessage());
            return;
        }

        broadcastState(state);
        triggerBotTurnIfNeeded(session);
    }

    // ============================================================
    //  3) PASS TURN  (vetëm te Kati 5 - Shtatat, kur s'ka lëvizje të vlefshme)
    // ============================================================
    @MessageMapping("/pass/{roomId}")
    public void passTurn(@DestinationVariable String roomId, @Payload MoveMessage msg) {
        GameSession session = requireRoom(roomId);
        GameState state = session.getState();
        Player player = findPlayer(state, msg.getPlayerId());

        try {
            gameService.confirmPass(state, player);
        } catch (IllegalStateException ex) {
            sendPrivateError(msg.getPlayerId(), ex.getMessage());
            return;
        }
        broadcastState(state);
        triggerBotTurnIfNeeded(session); // nëse dhënësi është bot, transferon automatikisht
    }

    // ============================================================
    //  4) TRADE CARD  (transferimi i detyrueshëm kur dikush është i bllokuar)
    // ============================================================
    @MessageMapping("/trade/{roomId}")
    public void tradeCard(@DestinationVariable String roomId, @Payload MoveMessage msg) {
        GameSession session = requireRoom(roomId);
        GameState state = session.getState();
        Player giver = findPlayer(state, msg.getPlayerId());
        Player blocked = findPlayer(state, msg.getTargetPlayerId());
        Card card = new Card(Suit.valueOf(msg.getSuit()), msg.getRank());

        try {
            gameService.tradeCard(state, giver, card, blocked);
        } catch (IllegalStateException ex) {
            sendPrivateError(msg.getPlayerId(), ex.getMessage());
            return;
        }
        broadcastState(state);
        triggerBotTurnIfNeeded(session);
    }

    // ============================================================
    //  LOGJIKA E BOT-EVE (ekzekutohet plotësisht në Backend)
    // ============================================================
    private void triggerBotTurnIfNeeded(GameSession session) {
        GameState state = session.getState();
        if (!session.isSoloMode()) return;
        if (state.getPhase() != GamePhase.KATE_1_4 && state.getPhase() != GamePhase.KATI_5_SHTATAT) return;

        // Rasti i veçantë: dikush është i bllokuar te Shtatat -> vetë dhënësi (mund të jetë bot) transferon
        if (state.getPhase() == GamePhase.KATI_5_SHTATAT && state.getBlockedPlayerSeat() != -1) {
            int blockedSeat = state.getBlockedPlayerSeat();
            int giverSeat = state.getBlockedGiverSeat();
            Player giver = giverSeat != -1 ? state.getPlayerBySeat(giverSeat) : null;
            if (giver != null && giver.isBot()) {
                botScheduler.schedule(() -> {
                    Player blockedP = state.getPlayerBySeat(blockedSeat);
                    Card give = botService.chooseCardToGiveWhenBlocked(state, giver);
                    gameService.tradeCard(state, giver, give, blockedP);
                    broadcastState(state);
                    triggerBotTurnIfNeeded(session);
                }, 700, TimeUnit.MILLISECONDS);
            }
            return;
        }

        Player current = state.getCurrentPlayer();
        if (current == null || !current.isBot()) return;

        botScheduler.schedule(() -> {
            try {
                if (state.getPhase() == GamePhase.KATE_1_4) {
                    Card choice = botService.chooseCardKate1to4(state, current, gameService);
                    gameService.playCardKate1to4(state, current, choice);
                } else if (state.getPhase() == GamePhase.KATI_5_SHTATAT) {
                    Optional<Card> choice = botService.chooseCardKati5(state, current, gameService);
                    if (choice.isPresent()) {
                        gameService.playCardKati5(state, current, choice.get());
                    } else {
                        gameService.confirmPass(state, current);
                    }
                }
            } catch (IllegalStateException ignored) { /* rast tjetërsor, humbi race */ }

            broadcastState(state);
            triggerBotTurnIfNeeded(session); // vazhdon zinxhirin nëse edhe lojtari tjetër është bot
        }, 800, TimeUnit.MILLISECONDS);
    }

    // ============================================================
    //  HELPERS
    // ============================================================
    private GameSession requireRoom(String roomId) {
        GameSession s = roomManager.getRoom(roomId);
        if (s == null) throw new IllegalStateException("Dhoma nuk ekziston: " + roomId);
        return s;
    }

    private Player findPlayer(GameState state, String playerId) {
        return state.getPlayers().stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Lojtari s'u gjet: " + playerId));
    }

    /** Dërgon gjendjen (të personalizuar - fsheh duart e të tjerëve) çdo lojtari individualisht */
    private void broadcastState(GameState state) {
        for (Player p : state.getPlayers()) {
            if (p.isBot()) continue;
            GameStateDto dto = GameStateDto.from(state, p.getId());
            messagingTemplate.convertAndSendToUser(p.getId(), "/queue/game-state", dto);
        }
        // Version publik (pa asnjë dorë) për ekranin e tavolinës / spektatorë
        messagingTemplate.convertAndSend("/topic/room/" + state.getRoomId(), GameStateDto.from(state, null));
    }

    private void sendPrivateError(String playerId, String message) {
        messagingTemplate.convertAndSendToUser(playerId, "/queue/errors", message);
    }
}
