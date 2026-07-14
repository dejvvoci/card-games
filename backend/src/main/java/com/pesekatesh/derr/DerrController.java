package com.pesekatesh.derr;

import com.pesekatesh.stats.GameResultService;
import com.pesekatesh.user.AuthTokenService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Controller
public class DerrController {

    private final DerrRoomManager roomManager;
    private final DerrService gameService;
    private final DerrBotService botService;
    private final SimpMessagingTemplate messagingTemplate;
    private final AuthTokenService authTokenService;
    private final GameResultService gameResultService;
    private final ScheduledExecutorService botScheduler = Executors.newSingleThreadScheduledExecutor();

    public DerrController(DerrRoomManager roomManager, DerrService gameService, DerrBotService botService,
                           SimpMessagingTemplate messagingTemplate, AuthTokenService authTokenService,
                           GameResultService gameResultService) {
        this.roomManager = roomManager;
        this.gameService = gameService;
        this.botService = botService;
        this.messagingTemplate = messagingTemplate;
        this.authTokenService = authTokenService;
        this.gameResultService = gameResultService;
    }

    // ============================================================
    //  1) JOIN ROOM  (Multiplayer real ose Solo me bot-e)
    // ============================================================
    @MessageMapping("/derr/join/{roomId}")
    public void joinRoom(@DestinationVariable String roomId, @Payload DerrJoinMessage msg) {
        DerrSession session = roomManager.getOrCreateRoom(roomId);
        DerrState state = session.getState();

        if (state.getPlayers().stream().noneMatch(p -> p.getId().equals(msg.getPlayerId())) && !session.isFull()) {
            int seat = state.getPlayers().size();
            DerrPlayer player = new DerrPlayer(msg.getPlayerId(), msg.getUsername(), seat, false);
            player.setUserId(authTokenService.resolveUserId(msg.getAuthToken()).orElse(null));
            state.getPlayers().add(player);
        }

        if (msg.isSoloVsBots()) {
            // SOLO MODE: plotëso menjëherë vendet e tjera me BOT
            fillRemainingSeatsWithBots(state);
        } else {
            // MULTIPLAYER: nëse brenda 60s nga hapja e dhomës vendet s'plotësohen me lojtarë të vërtetë,
            // plotësohen automatikisht me BOT dhe loja fillon
            scheduleLobbyBotFillIfNeeded(session);
        }

        broadcastState(state);
        startMatchIfReady(session);
    }

    /** Plotëson vendet bosh të mbetura me BOT (deri në 4 lojtarë) */
    private void fillRemainingSeatsWithBots(DerrState state) {
        while (state.getPlayers().size() < DerrState.SEAT_COUNT) {
            int seat = state.getPlayers().size();
            state.getPlayers().add(new DerrPlayer("bot-" + UUID.randomUUID(), "Bot " + seat, seat, true));
        }
    }

    /** Kur dhoma është plot (4 lojtarë, real ose BOT), fillo lojën nëse ende s'ka filluar */
    private void startMatchIfReady(DerrSession session) {
        DerrState state = session.getState();
        if (session.isFull() && state.getPhase() == DerrPhase.WAITING_FOR_PLAYERS) {
            gameService.dealAndCleanup(state);
            broadcastState(state);
            triggerBotTurnIfNeeded(session);
        }
    }

    /** Planifikon, vetëm një herë për këtë dhomë, mbushjen me BOT pas DerrState.LOBBY_BOT_FILL_MS */
    private void scheduleLobbyBotFillIfNeeded(DerrSession session) {
        DerrState state = session.getState();
        if (!state.markLobbyTimerScheduled()) return; // tashmë e planifikuar

        long delayMs = Math.max(0, state.getLobbyDeadlineEpochMs() - System.currentTimeMillis());
        botScheduler.schedule(() -> {
            if (state.getPhase() != DerrPhase.WAITING_FOR_PLAYERS) return; // loja tashmë filloi vetë
            fillRemainingSeatsWithBots(state);
            broadcastState(state);
            startMatchIfReady(session);
        }, delayMs, TimeUnit.MILLISECONDS);
    }

    /** Nëse është radha e një BOT-i, tërheq automatikisht pas një vonese të shkurtër */
    private void triggerBotTurnIfNeeded(DerrSession session) {
        DerrState state = session.getState();
        if (state.getPhase() != DerrPhase.PLAYING) return;
        DerrPlayer drawer = state.getDrawer();
        if (drawer == null || !drawer.isBot()) return;

        botScheduler.schedule(() -> {
            try {
                int index = botService.chooseCardIndex(state);
                gameService.drawCard(state, drawer, index);
            } catch (IllegalStateException ignored) { /* rast tjetërsor, humbi race */ }
            broadcastState(state);
            triggerBotTurnIfNeeded(session); // vazhdon zinxhirin nëse edhe lojtari tjetër është BOT
        }, 900, TimeUnit.MILLISECONDS);
    }

    // ============================================================
    //  2) TËRHEQ LETËR (VJEDH NGA HOLDER-I)
    // ============================================================
    @MessageMapping("/derr/draw/{roomId}")
    public void draw(@DestinationVariable String roomId, @Payload DerrMoveMessage msg) {
        DerrSession session = requireRoom(roomId);
        DerrState state = session.getState();
        DerrPlayer drawer = findPlayer(state, msg.getPlayerId());

        try {
            gameService.drawCard(state, drawer, msg.getCardIndex());
        } catch (IllegalStateException ex) {
            sendPrivateError(msg.getPlayerId(), ex.getMessage());
            return;
        }

        broadcastState(state);
        triggerBotTurnIfNeeded(session);
    }

    // ============================================================
    //  HELPERS
    // ============================================================
    private DerrSession requireRoom(String roomId) {
        DerrSession s = roomManager.getRoom(roomId);
        if (s == null) throw new IllegalStateException("Dhoma nuk ekziston: " + roomId);
        return s;
    }

    private DerrPlayer findPlayer(DerrState state, String playerId) {
        return state.getPlayers().stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Lojtari s'u gjet: " + playerId));
    }

    /** Dërgon gjendjen (të personalizuar - fsheh duart e të tjerëve) çdo lojtari individualisht */
    private void broadcastState(DerrState state) {
        for (DerrPlayer p : state.getPlayers()) {
            if (p.isBot()) continue;
            DerrStateDto dto = DerrStateDto.from(state, p.getId());
            messagingTemplate.convertAndSendToUser(p.getId(), "/queue/derr-state", dto);
        }
        messagingTemplate.convertAndSend("/topic/derr/room/" + state.getRoomId(), DerrStateDto.from(state, null));

        // Ndeshja sapo mbaroi -> regjistro historikun e statistikave (vetëm një herë) për lojtarët e loguar
        if (state.getPhase() == DerrPhase.GAME_OVER && state.markResultsRecorded()) {
            gameResultService.recordDerrResult(state);
        }
    }

    private void sendPrivateError(String playerId, String message) {
        messagingTemplate.convertAndSendToUser(playerId, "/queue/derr-errors", message);
    }
}
