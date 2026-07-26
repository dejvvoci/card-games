package com.pesekatesh.service;

import com.pesekatesh.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class GameService {

    // ============================================================
    //  SHPËRNDARJA E LETRAVE
    // ============================================================

    public List<Card> buildFullDeck() {
        List<Card> deck = new ArrayList<>();
        for (Suit s : Suit.values()) {
            for (int rank = 2; rank <= 14; rank++) {
                deck.add(new Card(s, rank));
            }
        }
        Collections.shuffle(deck);
        return deck; // 52 letra
    }

    /** Fillon një raund të ri: shpërndan 13 letra secilit dhe kalon te faza KATE_1_4 */
    public void dealNewRound(GameState state, int dealerSeat) {
        dealHandsOnly(state);
        state.setPhase(GamePhase.KATE_1_4);
        // Lojtari pas dealerit fillon dorën e parë
        state.setCurrentPlayerIndex((dealerSeat + 1) % 4);
        state.setLedSuit(null);
    }

    /** Vetëm shkrin+shpërndan letrat dhe pastron gjendjen e raundit, pa caktuar fazën (ripërdoret edhe nga raundi vendimtar Shtatat-only) */
    private void dealHandsOnly(GameState state) {
        state.resetForNewRound();
        List<Card> deck = buildFullDeck();
        List<Player> players = state.getPlayers();
        for (int i = 0; i < 13; i++) {
            for (Player p : players) {
                p.getHand().add(deck.remove(0));
            }
        }
        for (Player p : players) {
            p.getHand().sort(Comparator.comparing(Card::getSuit).thenComparing(Card::getRank));
        }
    }

    // ============================================================
    //  KATE 1-4 : TRICK-TAKING (Derr / Çupat / Kupat / Marrjet)
    // ============================================================

    /** Kontrollon nëse lëvizja respekton rregullin e "ndjekjes së shenjës" */
    public boolean isValidTrickMove(GameState state, Player player, Card card) {
        if (!player.getHand().contains(card)) return false;

        if (state.getCurrentTrick().isEmpty()) {
            return true; // ai që hap dorën mund të luajë çdo letër
        }
        Suit led = state.getLedSuit();
        boolean hasLedSuit = player.hasSuit(led);
        if (hasLedSuit) {
            // Duhet detyrimisht ta ndjekë shenjën
            return card.getSuit() == led;
        }
        // Nuk ka shenjën -> mund të "shpresë" me çdo letër tjetër
        return true;
    }

    /**
     * Luan një letër në trick-un aktual. Kthen true nëse trick-u u kompletua
     * (4 letra) dhe u zgjidh automatikisht (fituesi u caktua, letrat u kapën).
     */
    public boolean playCardKate1to4(GameState state, Player player, Card card) {
        if (!isValidTrickMove(state, player, card)) {
            throw new IllegalStateException("Lëvizje e pavlefshme: duhet të ndiqet shenja " + state.getLedSuit());
        }
        player.getHand().remove(card);
        if (state.getCurrentTrick().isEmpty()) {
            state.setLedSuit(card.getSuit());
        }
        state.getCurrentTrick().put(player.getSeatIndex(), card);

        if (state.getCurrentTrick().size() == 4) {
            resolveTrick(state);
            return true;
        } else {
            state.nextTurn();
            return false;
        }
    }

    private void resolveTrick(GameState state) {
        Suit led = state.getLedSuit();
        int winningSeat = -1;
        Card winningCard = null;
        for (Map.Entry<Integer, Card> e : state.getCurrentTrick().entrySet()) {
            Card c = e.getValue();
            if (c.getSuit() != led) continue; // vetëm letrat e shenjës konkurrojnë
            if (winningCard == null || c.getRank() > winningCard.getRank()) {
                winningCard = c;
                winningSeat = e.getKey();
            }
        }
        // Kapja e trick-ut (grup prej 4 letrash) nga fituesi
        state.getCapturedTricks().get(winningSeat).add(new ArrayList<>(state.getCurrentTrick().values()));

        // Ruaj pamjen e trick-ut për animacionin e mbledhjes në frontend (currentTrick pastrohet menjëherë më poshtë)
        state.getLastTrickCards().clear();
        state.getLastTrickCards().putAll(state.getCurrentTrick());
        state.setLastTrickWinnerSeat(winningSeat);
        state.incrementTrickSeq();

        state.getCurrentTrick().clear();
        state.setLedSuit(null);
        state.setCurrentPlayerIndex(winningSeat); // fituesi hap dorën tjetër

        // A ka mbaruar raundi (të gjithë me dorë bosh)?
        boolean roundOver = state.getPlayers().stream().allMatch(Player::isHandEmpty);
        if (roundOver) {
            calculateKate1to4Scores(state);
            if (state.isShtatatEveryRound()) {
                state.setPhase(GamePhase.KATI_5_SHTATAT); // GameController thërret startKati5() menjëherë pas kësaj
            } else {
                state.setPhase(GamePhase.ROUND_FINISHED); // Shtatat vetëm si raund vendimtar në fund — kapërcehet këtu
            }
        }
    }

    /** Llogarit pikët e Kateve 1-4 (të katërta të luajtura njëkohësisht) */
    public void calculateKate1to4Scores(GameState state) {
        for (Player p : state.getPlayers()) {
            int seat = p.getSeatIndex();
            List<List<Card>> tricks = state.getCapturedTricks().getOrDefault(seat, List.of());
            int scoreDelta = tricks.size() * 2; // +2 për çdo dorë (max +26)

            for (List<Card> trick : tricks) {
                for (Card c : trick) {
                    if (c.isDerrMac()) scoreDelta -= 16;   // Derr Maç
                    if (c.isQueen())   scoreDelta -= 4;    // çdo Çupë (-4, total -16)
                    if (c.isZemer())   scoreDelta -= 2;    // çdo Zemër (-2, total -26)
                }
            }
            p.addScore(scoreDelta);
        }
    }

    // ============================================================
    //  KATI 5 : SHTATAT
    // ============================================================

    /**
     * Përgatit fazën e shtatave: Kate 1-4 e konsumon gjithë kalonën (13 dorë × 4 lojtarë),
     * kështu që Shtatat është një nën-lojë e veçantë me shpërndarje krejt të re letrash.
     * Pastaj gjen kush ka 7♣ dhe e cakton si radhë e parë.
     */
    public void startKati5(GameState state) {
        List<Card> deck = buildFullDeck();
        List<Player> players = state.getPlayers();
        for (Player p : players) p.getHand().clear();
        for (int i = 0; i < 13; i++) {
            for (Player p : players) p.getHand().add(deck.remove(0));
        }
        for (Player p : players) {
            p.getHand().sort(Comparator.comparing(Card::getSuit).thenComparing(Card::getRank));
        }

        state.getSevensBounds().clear();
        state.getFinishOrder().clear();
        state.setBlockedPlayerSeat(-1);
        state.setBlockedGiverSeat(-1);
        for (Player p : state.getPlayers()) {
            if (p.getHand().stream().anyMatch(Card::isSeventOfSpathi)) {
                state.setCurrentPlayerIndex(p.getSeatIndex());
                break;
            }
        }
        state.setPhase(GamePhase.KATI_5_SHTATAT);
    }

    /** Kontrollon nëse letra mund të luhet në sekuencën e shtatave */
    public boolean isValidSevensMove(GameState state, Card card) {
        int[] bounds = state.getSevensBounds().get(card.getSuit());
        if (bounds == null) {
            return card.getRank() == 7; // vetëm 7-a mund të hapë shenjë të re
        }
        int low = bounds[0], high = bounds[1];
        return card.getRank() == low - 1 || card.getRank() == high + 1;
    }

    /** A ka lojtari ndonjë lëvizje të vlefshme në dorën e tij? */
    public boolean hasValidSevensMove(GameState state, Player player) {
        return player.getHand().stream().anyMatch(c -> isValidSevensMove(state, c));
    }

    public void playCardKati5(GameState state, Player player, Card card) {
        if (!isValidSevensMove(state, card) || !player.getHand().contains(card)) {
            throw new IllegalStateException("Lëvizje e pavlefshme te Shtatat për letrën " + card);
        }
        player.getHand().remove(card);
        int[] bounds = state.getSevensBounds().get(card.getSuit());
        if (bounds == null) {
            state.getSevensBounds().put(card.getSuit(), new int[]{7, 7});
        } else if (card.getRank() == bounds[0] - 1) {
            bounds[0] = card.getRank();
        } else {
            bounds[1] = card.getRank();
        }

        if (player.isHandEmpty()) {
            state.getFinishOrder().add(player.getSeatIndex());
        }
        advanceSevensTurn(state);
        checkSevensGameEnd(state);
    }

    /**
     * Kalon radhën. Nëse lojtari tjetër nuk ka lëvizje të vlefshme, shënohet si i
     * "bllokuar" dhe pritet një `tradeCard` nga lojtari paraardhës (ai që sapo luajti).
     */
    private void advanceSevensTurn(GameState state) {
        int giverSeat = state.getCurrentPlayerIndex(); // lojtari që sapo luajti, para se radha të kalojë
        int next = nextActiveSeat(state, giverSeat);
        if (next == -1) return; // të gjithë kanë mbaruar

        Player nextPlayer = state.getPlayerBySeat(next);
        if (!hasValidSevensMove(state, nextPlayer)) {
            // Bllokim: lojtari që sapo luajti (jo domosdoshmërisht "seat-1", nëse dikush tjetër ka mbaruar tashmë) duhet t'i japë një letër
            state.setBlockedPlayerSeat(next);
            state.setBlockedGiverSeat(giverSeat);
        } else {
            state.setBlockedPlayerSeat(-1);
            state.setBlockedGiverSeat(-1);
        }
        state.setCurrentPlayerIndex(next);
    }

    private int nextActiveSeat(GameState state, int fromSeat) {
        for (int i = 1; i <= 4; i++) {
            int candidate = (fromSeat + i) % 4;
            Player p = state.getPlayerBySeat(candidate);
            if (!p.isHandEmpty()) return candidate;
        }
        return -1;
    }

    /** Lojtari aktual konfirmon 'Pass' (backend rivërteton se s'ka lëvizje) */
    public void confirmPass(GameState state, Player player) {
        if (hasValidSevensMove(state, player)) {
            throw new IllegalStateException("Ke lëvizje të vlefshme, s'mund të bësh Pass.");
        }
        int giverSeat = previousActiveSeat(state, player.getSeatIndex());
        if (giverSeat == -1) {
            // Askush tjetër s'ka letra për t'i dhënë (ky ishte lojtari i fundit aktiv) -> raundi mbaron këtu
            if (!state.getFinishOrder().contains(player.getSeatIndex())) {
                state.getFinishOrder().add(player.getSeatIndex());
            }
            checkSevensGameEnd(state);
            return;
        }
        state.setBlockedPlayerSeat(player.getSeatIndex());
        state.setBlockedGiverSeat(giverSeat);
    }

    /**
     * Lojtari që sapo luajti (jo domosdoshmërisht "seat-i para" — shih {@link #getBlockedGiverSeat}) i transferon një letër lojtarit të bllokuar.
     * @param givenCard letra e zgjedhur nga dhënësi (te AI zgjidhet strategjikisht, shih BotService)
     */
    public void tradeCard(GameState state, Player giver, Card givenCard, Player blockedPlayer) {
        if (state.getBlockedPlayerSeat() != blockedPlayer.getSeatIndex()) {
            throw new IllegalStateException("Nuk ka bllokim aktiv për këtë lojtar.");
        }
        if (giver.getSeatIndex() != state.getBlockedGiverSeat()) {
            throw new IllegalStateException("Ky lojtar nuk është dhënësi i pritur për këtë bllokim.");
        }
        if (!giver.getHand().remove(givenCard)) {
            throw new IllegalStateException("Dhënësi nuk e zotëron këtë letër.");
        }
        blockedPlayer.getHand().add(givenCard);
        state.setCurrentPlayerIndex(blockedPlayer.getSeatIndex()); // provon sërish të luajë

        // Letra e dhënë nuk zgjidhet gjithmonë vetë sekuencën e lojtarit të bllokuar (as bot-et, as njerëzit
        // s'detyrohen ta zgjedhin një letër që patjetër hap një lëvizje) — rikontrollo, përndryshe loja
        // ngrinte këtu: `blockedPlayerSeat` pastrohej pa kusht dhe askush s'e vazhdonte lojën.
        if (hasValidSevensMove(state, blockedPlayer)) {
            state.setBlockedPlayerSeat(-1);
            state.setBlockedGiverSeat(-1);
        } else {
            int nextGiverSeat = previousActiveSeat(state, blockedPlayer.getSeatIndex());
            if (nextGiverSeat == -1) {
                // Askush tjetër s'ka letra për t'i dhënë -> ky lojtar mbetet i fundit, raundi mbaron këtu
                if (!state.getFinishOrder().contains(blockedPlayer.getSeatIndex())) {
                    state.getFinishOrder().add(blockedPlayer.getSeatIndex());
                }
                checkSevensGameEnd(state);
                return;
            }
            state.setBlockedPlayerSeat(blockedPlayer.getSeatIndex());
            state.setBlockedGiverSeat(nextGiverSeat);
        }
    }

    /** Kërkon prapa (duke anashkaluar lojtarët që kanë mbaruar) seat-in aktiv më të fundit para fromSeat */
    private int previousActiveSeat(GameState state, int fromSeat) {
        for (int i = 1; i <= 4; i++) {
            int candidate = (fromSeat - i + 4) % 4;
            Player p = state.getPlayerBySeat(candidate);
            if (!p.isHandEmpty()) return candidate;
        }
        return -1;
    }

    private void checkSevensGameEnd(GameState state) {
        long remaining = state.getPlayers().stream().filter(p -> !p.isHandEmpty()).count();
        if (remaining <= 1) {
            // shto lojtarin e fundit (nëse mbeti) në renditje
            for (Player p : state.getPlayers()) {
                if (!state.getFinishOrder().contains(p.getSeatIndex())) {
                    state.getFinishOrder().add(p.getSeatIndex());
                }
            }
            calculateSevensScores(state);
            state.setBlockedPlayerSeat(-1); // Shtatat mbaroi -> asnjë bllokim s'duhet të mbetet i dukshëm pas raundit
            state.setBlockedGiverSeat(-1);
            state.setPhase(GamePhase.ROUND_FINISHED);
        }
    }

    private static final int[] SEVENS_PLACEMENT_POINTS = {32, 16, 8, 4};

    public void calculateSevensScores(GameState state) {
        List<Integer> order = state.getFinishOrder();
        for (int i = 0; i < order.size() && i < 4; i++) {
            Player p = state.getPlayerBySeat(order.get(i));
            p.addScore(SEVENS_PLACEMENT_POINTS[i]);
        }
    }

    // ============================================================
    //  STRUKTURA E NDESHJES: 5 RAUNDE + GATI-UP + MËNYRA E SHTATAVE
    // ============================================================

    /**
     * Thirret kur të gjithë lojtarët human kanë klikuar "gati" pas ROUND_FINISHED.
     * Vendos nëse loja vazhdon me raundin tjetër, hyn në raundin vendimtar Shtatat,
     * ose mbyllet përfundimisht.
     */
    public void advanceToNextRoundOrFinish(GameState state) {
        state.getReadyPlayerIds().clear();

        if (state.isTiebreakRound()) {
            finishMatch(state); // raundi vendimtar sapo mbaroi -> ndeshja mbyllet gjithsesi
            return;
        }

        if (state.getRoundNumber() >= state.getTotalRounds()) {
            if (!state.isShtatatEveryRound() && hasScoreTie(state)) {
                startTiebreakRound(state);
            } else {
                finishMatch(state);
            }
            return;
        }

        state.setRoundNumber(state.getRoundNumber() + 1);
        dealNewRound(state, 0);
    }

    private boolean hasScoreTie(GameState state) {
        int max = state.getPlayers().stream().mapToInt(Player::getTotalScore).max().orElse(0);
        long countAtMax = state.getPlayers().stream().filter(p -> p.getTotalScore() == max).count();
        return countAtMax >= 2;
    }

    private void startTiebreakRound(GameState state) {
        state.setTiebreakRound(true);
        state.resetForNewRound(); // pastron gjendjen e raundit; startKati5() shpërndan vetë letrat e Shtatave
        startKati5(state);
    }

    private void finishMatch(GameState state) {
        int winnerSeat = state.getPlayers().stream()
                .max(Comparator.comparingInt(Player::getTotalScore))
                .map(Player::getSeatIndex).orElse(0);
        state.setMatchWinnerSeat(winnerSeat);
        state.setPhase(GamePhase.GAME_OVER);
    }
}
