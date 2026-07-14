package com.pesekatesh.derr;

import com.pesekatesh.model.Card;
import com.pesekatesh.model.Suit;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Rregullat e "Derri në Dorë": deck standard pa 3 nga 4 Mbretërit (mbetet vetëm K♠ = "Derri"),
 * letra shpërndahen krejtësisht (49 letra mes 4 vendesh), çdo lojtar djeg çiftet e veta fillestare,
 * pastaj lojtarët tërheqin letra t'verbëra njëri nga tjetri në drejtim orar deri sa të mbetet
 * vetëm një lojtar me letrën e pambetur pa çift — ai është "Derri".
 */
@Service
public class DerrService {

    // ============================================================
    //  NDËRTIMI I DECK-UT DHE SHPËRNDARJA
    // ============================================================

    /** Deck standard 52-letërsh, minus Mbretin e Zemrës, Rombit dhe Spathisë (mbetet vetëm K♠ = "Derri") */
    private List<Card> buildDeckWithoutThreeKings() {
        List<Card> deck = new ArrayList<>();
        for (Suit s : Suit.values()) {
            for (int rank = 2; rank <= 14; rank++) {
                if (rank == 13 && s != Suit.MAC) continue; // heq K♥, K♦, K♣
                deck.add(new Card(s, rank));
            }
        }
        Collections.shuffle(deck);
        return deck;
    }

    /**
     * Shpërndan të gjitha letrat (49) mes 4 vendeve radhazi (disa marrin një letër më shumë),
     * djeg çiftet fillestare të secilit lojtar, dhe cakton holder/drawer fillestar.
     */
    public void dealAndCleanup(DerrState state) {
        List<Card> deck = buildDeckWithoutThreeKings();
        List<DerrPlayer> players = state.getPlayers();
        for (DerrPlayer p : players) {
            p.getHand().clear();
            p.setActive(true);
        }
        state.getFinishOrder().clear();
        state.getBurnedPairs().clear();
        state.setLastEscapedSeat(-1);
        state.setMatchLoserSeat(-1);

        int seat = 0;
        while (!deck.isEmpty()) {
            DerrPlayer p = state.getPlayerBySeat(seat % DerrState.SEAT_COUNT);
            p.getHand().add(deck.remove(0));
            seat++;
        }

        for (DerrPlayer p : players) {
            state.getBurnedPairs().addAll(cleanupPairs(p));
            if (p.isHandEmpty()) {
                markEscaped(state, p);
            }
        }

        // Seat 0 merr gjithmonë letrën "ekstra" (49 s'ndahet plotësisht me 4) -> ai fillon si "holder"
        int startingHolder = state.getPlayerBySeat(0).isActive() ? 0 : state.previousActiveSeat(0);
        state.setHolderSeat(startingHolder);
        state.setDrawerSeat(state.nextActiveSeat(startingHolder));

        checkGameEnd(state);
        if (state.getPhase() != DerrPhase.GAME_OVER) {
            state.setPhase(DerrPhase.PLAYING);
        }
    }

    /** Heq nga dora çdo çift (2 letra të njëjtën vlerë); nëse ka 4 të njëjta, digjen si 2 çifte */
    private List<Card> cleanupPairs(DerrPlayer player) {
        Map<Integer, List<Card>> byRank = new TreeMap<>();
        for (Card c : player.getHand()) {
            byRank.computeIfAbsent(c.getRank(), k -> new ArrayList<>()).add(c);
        }
        List<Card> burned = new ArrayList<>();
        List<Card> keep = new ArrayList<>();
        for (List<Card> cards : byRank.values()) {
            int pairedCount = (cards.size() / 2) * 2;
            burned.addAll(cards.subList(0, pairedCount));
            keep.addAll(cards.subList(pairedCount, cards.size()));
        }
        player.getHand().clear();
        player.getHand().addAll(keep);
        return burned;
    }

    // ============================================================
    //  TËRHEQJA (VJEDHJA) E LETRËS
    // ============================================================

    /**
     * Lojtari me radhë (drawer) tërheq letrën në pozicionin cardIndex nga dora e holder-it (e verbër).
     * Nëse formon çift me një letër që ai kishte tashmë, e digjet menjëherë.
     */
    public void drawCard(DerrState state, DerrPlayer drawer, int cardIndex) {
        if (state.getPhase() != DerrPhase.PLAYING) {
            throw new IllegalStateException("Loja s'është duke u luajtur.");
        }
        if (state.getDrawerSeat() != drawer.getSeatIndex()) {
            throw new IllegalStateException("Nuk është radha jote.");
        }
        DerrPlayer holder = state.getHolder();
        if (holder == null || cardIndex < 0 || cardIndex >= holder.getHand().size()) {
            throw new IllegalStateException("Pozicion i pavlefshëm letre.");
        }

        Card drawn = holder.getHand().remove(cardIndex);
        Optional<Card> match = drawer.getHand().stream().filter(c -> c.getRank() == drawn.getRank()).findFirst();
        if (match.isPresent()) {
            Card matched = match.get();
            drawer.getHand().remove(matched);
            state.getBurnedPairs().add(drawn);
            state.getBurnedPairs().add(matched);
        } else {
            drawer.getHand().add(drawn);
        }

        if (holder.isHandEmpty()) {
            markEscaped(state, holder);
        }
        if (drawer.isHandEmpty()) {
            markEscaped(state, drawer);
        }

        if (checkGameEnd(state)) {
            return;
        }

        // Cakto holder/drawer e radhës tjetër
        int drawerSeatBefore = drawer.getSeatIndex();
        int holderSeatBefore = holder.getSeatIndex();
        int newHolderSeat;
        if (state.getPlayerBySeat(drawerSeatBefore).isActive()) {
            newHolderSeat = drawerSeatBefore;
        } else if (state.getPlayerBySeat(holderSeatBefore).isActive()) {
            newHolderSeat = holderSeatBefore;
        } else {
            // Të dy u zbrazën në të njëjtën radhë -> gjej më të afërmin aktiv para vendit të drawer-it
            newHolderSeat = state.previousActiveSeat(drawerSeatBefore);
        }
        state.setHolderSeat(newHolderSeat);
        state.setDrawerSeat(state.nextActiveSeat(newHolderSeat));
    }

    private void markEscaped(DerrState state, DerrPlayer player) {
        if (!player.isActive()) return; // tashmë i shënuar (mund të thirret dy herë brenda checkGameEnd)
        player.setActive(false);
        state.getFinishOrder().add(player.getSeatIndex());
        state.setLastEscapedSeat(player.getSeatIndex());
        state.incrementEscapeSeq();
    }

    /** Kthen true nëse loja mbaroi (mbeti vetëm 1 lojtar aktiv -> ai është "Derri") */
    private boolean checkGameEnd(DerrState state) {
        List<Integer> active = state.activeSeats();
        if (active.size() <= 1) {
            if (active.size() == 1) {
                int loserSeat = active.get(0);
                state.setMatchLoserSeat(loserSeat);
                if (!state.getFinishOrder().contains(loserSeat)) {
                    state.getFinishOrder().add(loserSeat);
                }
            }
            state.setPhase(DerrPhase.GAME_OVER);
            return true;
        }
        return false;
    }
}
