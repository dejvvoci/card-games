package com.pesekatesh.peseqindsh.service;

import com.pesekatesh.model.Card;
import com.pesekatesh.model.Suit;
import com.pesekatesh.peseqindsh.model.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SHËNIM MBI SUPOZIMET (rregulla që teksti origjinal i linte të nënkuptuara):
 * 1. Sekuenca e radhës: MELD (opsionale) -> DISCARD (i detyrueshëm) -> DRAW (i detyrueshëm,
 *    ose "merr gjithë tokën" nëse lojtari është tashmë i shtruar).
 * 2. Kur dikush merr GJITHË letrat e hapura, ato duhet përdorur 100% në kombinime
 *    (të reja ose zgjatje) para se radha t'i kalojë kundërshtarit — s'ka nevojë për
 *    hedhje shtesë sepse hedhja e këtij tarni tashmë u krye si hapi 1.
 * 3. Zgjatja e kombinimeve (Seksioni 5) lejohet mbi çdo kombinim në tokë, jo vetëm mbi
 *    ato të vetat — konvencion standard i Rami/Rummy 500.
 * 4. Vlera e letrave individuale (për prag 25p dhe humbësin në fund): 3-8=5p, 9-K=10p,
 *    A=15p, 2(xhoker)=20p — të nxjerra në përputhje me "kaçet" e dhëna (40/60/200).
 */
@Service
public class PeseqindshService {

    // ============================================================
    //  NDËRTIMI I DEK-UT, PRERJA, SHPËRNDARJA
    // ============================================================

    private List<Card> buildFullDeck() {
        List<Card> deck = new ArrayList<>();
        for (Suit s : Suit.values()) {
            for (int rank = 2; rank <= 14; rank++) {
                deck.add(new Card(s, rank));
            }
        }
        Collections.shuffle(deck);
        return deck;
    }

    /** Fillon raund të ri: prerja e letrave + shpërndarja e 8 letrave secilit */
    public void startNewRound(PeseqindshState state, int cutterSeat) {
        state.resetForNewRound();
        state.setCutterSeat(cutterSeat);
        List<Card> deck = buildFullDeck();

        // --- Rregulli i Prerjes ---
        int cutIndex = 5 + new Random().nextInt(Math.max(1, deck.size() - 10));
        Card cutCard = deck.get(cutIndex);
        PeseqindshPlayer cutter = state.getPlayerBySeat(cutterSeat);
        if (cutCard.getRank() == 2) {
            deck.remove(cutIndex);
            cutter.getHand().add(cutCard); // Dyshi i kalon automatikisht cutterit
        }

        // --- Shpërndarja: 8 letra secilit ---
        for (PeseqindshPlayer p : state.getPlayers()) {
            for (int i = 0; i < 8; i++) {
                p.getHand().add(deck.remove(0));
            }
        }
        state.getClosedPile().addAll(deck); // pjesa tjetër -> Talon i mbyllur

        state.setCurrentPlayerSeat(cutterSeat); // lojtari që preu, hap raundin
        state.setPhase(PeseqindshPhase.PLAYING);
    }

    // ============================================================
    //  VLERËSIMI I LETRAVE DHE KOMBINIMEVE
    // ============================================================

    public int cardValue(Card c) {
        if (c.getRank() == 2) return 20;                         // xhoker (vlerë referuese)
        if (c.getRank() == 14) return 15;                        // Ass
        if (c.getRank() >= 9 && c.getRank() <= 13) return 10;    // 9,10,J,Q,K
        return 5;                                                 // 3..8
    }

    /** Llogarit pikët e një kombinimi, duke zbatuar "kaçet" speciale kur aplikohen */
    public int meldPoints(Meld meld) {
        List<Card> cards = meld.getCards();
        if (meld.getType() == MeldType.SET && cards.size() == 4) {
            if (cards.stream().allMatch(c -> c.getRank() == 2)) return 200;   // 4 Dyshat
            if (cards.stream().allMatch(c -> c.getRank() == 14)) return 60;   // 4 Asat
            if (meld.getRank() != null && meld.getRank() >= 9 && meld.getRank() <= 13
                    && cards.stream().allMatch(c -> c.getRank() == 2 || c.getRank() == meld.getRank())) {
                return 40; // 4 letra të njëjta Derr..9
            }
        }
        return cards.stream().mapToInt(this::cardValue).sum();
    }

    /**
     * Ndërton dhe validon një kombinim të ri nga letrat e propozuara.
     * Mbështet Set (3-4 të njëjtën vlerë) dhe Run/Kolor (3+ radhazi, të njëjtën lule),
     * me Dyshat (2) si Xhoker për të mbushur boshllëqet.
     */
    public Meld validateAndBuildMeld(List<Card> cards, int ownerSeat) {
        if (cards.size() < 3) {
            throw new IllegalStateException("Një kombinim duhet të ketë të paktën 3 letra.");
        }
        long jokerCount = cards.stream().filter(c -> c.getRank() == 2).count();
        List<Card> real = cards.stream().filter(c -> c.getRank() != 2).toList();

        // Rasti special: 4 Dyshat bashkë si kombinim i vetin (jo si xhoker për diçka tjetër)
        if (real.isEmpty() && cards.size() >= 3) {
            Meld meld = new Meld(MeldType.SET, ownerSeat);
            meld.setRank(2);
            meld.getCards().addAll(cards);
            return meld;
        }

        boolean sameSuit = real.stream().map(Card::getSuit).distinct().count() == 1;
        boolean sameRank = real.stream().map(Card::getRank).distinct().count() == 1;

        if (sameRank && !sameSuit) {
            return buildSet(cards, real, ownerSeat);
        }
        if (sameSuit) {
            // provo si Run; nëse letrat reale kanë të njëjtën vlerë (p.sh. vetëm 1 letër reale),
            // e trajtojmë si Set (rast i paqartë me pak letra) - preferojmë Set kur real.size()==1
            if (real.size() == 1 || !sameRank) {
                return buildRun(cards, real, ownerSeat);
            }
        }
        throw new IllegalStateException("Kombinim i pavlefshëm: letrat s'formojnë as Set as Kolor.");
    }

    private Meld buildSet(List<Card> cards, List<Card> real, int ownerSeat) {
        if (cards.size() > 4) throw new IllegalStateException("Një Set s'mund të ketë më shumë se 4 letra.");
        long distinctSuits = real.stream().map(Card::getSuit).distinct().count();
        if (distinctSuits != real.size()) {
            throw new IllegalStateException("Set-i s'mund të ketë dy letra të së njëjtës lule.");
        }
        Meld meld = new Meld(MeldType.SET, ownerSeat);
        meld.setRank(real.get(0).getRank());
        meld.getCards().addAll(cards);
        return meld;
    }

    private Meld buildRun(List<Card> cards, List<Card> real, int ownerSeat) {
        Suit suit = real.isEmpty() ? null : real.get(0).getSuit();
        List<Integer> ranks = real.stream().map(Card::getRank).sorted().toList();
        if (ranks.size() != new HashSet<>(ranks).size()) {
            throw new IllegalStateException("Kolor-i s'mund të përsërisë të njëjtën vlerë.");
        }
        int jokerCount = cards.size() - real.size();

        if (!real.isEmpty()) {
            int min = ranks.get(0), max = ranks.get(ranks.size() - 1);
            int span = max - min + 1;
            int internalGaps = span - real.size();
            if (internalGaps > jokerCount) {
                throw new IllegalStateException("Xhokerët s'mjaftojnë për të mbushur boshllëqet e Kolor-it.");
            }
            int leftoverJokers = jokerCount - internalGaps;
            int room = (min - 3) + (14 - max); // hapësira e mundshme për zgjatje 3..Ass(14)
            if (leftoverJokers > room) {
                throw new IllegalStateException("Kolor-i del jashtë kufijve të vlefshëm (3..Ass).");
            }
        }

        Meld meld = new Meld(MeldType.RUN, ownerSeat);
        meld.setSuit(suit);
        meld.getCards().addAll(cards);
        return meld;
    }

    // ============================================================
    //  HAPJA E LOJËS (25 PIKË) DHE SHTIMI I KOMBINIMEVE TË REJA
    // ============================================================

    /** Hapja e parë: një ose disa kombinime njëkohësisht që totalizojnë >= 25 pikë */
    public void openHand(PeseqindshState state, PeseqindshPlayer player, List<List<Card>> meldGroups) {
        if (player.isHasOpened()) {
            throw new IllegalStateException("Je tashmë i shtruar.");
        }
        List<Meld> built = new ArrayList<>();
        int total = 0;
        for (List<Card> group : meldGroups) {
            assertPlayerOwnsCards(player, group);
            Meld meld = validateAndBuildMeld(group, player.getSeatIndex());
            built.add(meld);
            total += meldPoints(meld);
        }
        if (total < PeseqindshState.OPENING_THRESHOLD) {
            throw new IllegalStateException("Kombinimet totalizojnë " + total + " pikë, minimumi është "
                    + PeseqindshState.OPENING_THRESHOLD + ".");
        }
        for (Meld m : built) {
            removeCardsFromHand(player, m.getCards());
            state.getMelds().add(m);
        }
        player.setHasOpened(true);
    }

    /** Pasi je i shtruar: shto një kombinim të ri pa kufizim pikësh */
    public void addMeld(PeseqindshState state, PeseqindshPlayer player, List<Card> cards) {
        if (!player.isHasOpened()) {
            throw new IllegalStateException("Duhet të bësh hapjen (25p) para se të shtosh kombinime të tjera.");
        }
        assertPlayerOwnsCards(player, cards);
        Meld meld = validateAndBuildMeld(cards, player.getSeatIndex());
        removeCardsFromHand(player, cards);
        state.getMelds().add(meld);
        state.getPendingForcedCards().removeAll(cards);
    }

    /** Zgjat një kombinim ekzistues në tokë (të vetin ose të kundërshtarit) me 1 letër */
    public void extendMeld(PeseqindshState state, PeseqindshPlayer player, String meldId, Card card) {
        if (!player.isHasOpened()) {
            throw new IllegalStateException("Duhet të jesh i shtruar për të zgjatur kombinime.");
        }
        Meld meld = state.getMelds().stream().filter(m -> m.getId().equals(meldId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Kombinimi s'u gjet."));
        assertPlayerOwnsCards(player, List.of(card));

        if (meld.getType() == MeldType.SET) {
            if (meld.getCards().size() >= 4) throw new IllegalStateException("Set-i është plot (4 letra).");
            if (card.getRank() != 2 && !Objects.equals(card.getRank(), meld.getRank())) {
                throw new IllegalStateException("Letra s'i përket vlerës së këtij Set-i.");
            }
        } else { // RUN
            List<Integer> ranks = meld.getCards().stream().map(Card::getRank)
                    .filter(r -> r != 2).sorted().toList();
            if (card.getRank() != 2 && card.getSuit() != meld.getSuit()) {
                throw new IllegalStateException("Letra duhet të jetë e së njëjtës lule.");
            }
            if (!ranks.isEmpty()) {
                int min = ranks.get(0), max = ranks.get(ranks.size() - 1);
                boolean extendsEdge = card.getRank() == min - 1 || card.getRank() == max + 1;
                if (card.getRank() != 2 && !extendsEdge) {
                    throw new IllegalStateException("Letra duhet të zgjasë Kolor-in në njërin skaj.");
                }
            }
        }
        removeCardsFromHand(player, List.of(card));
        meld.getCards().add(card);
        state.getPendingForcedCards().remove(card);
    }

    private void assertPlayerOwnsCards(PeseqindshPlayer player, List<Card> cards) {
        for (Card c : cards) {
            if (!player.getHand().contains(c)) {
                throw new IllegalStateException("Nuk e ke letrën " + c + " në dorë.");
            }
        }
    }

    private void removeCardsFromHand(PeseqindshPlayer player, List<Card> cards) {
        for (Card c : cards) player.getHand().remove(c);
    }

    // ============================================================
    //  DISCARD / DRAW (turn flow)
    // ============================================================

    /** Hap 1 i radhës: hedh një letër në grumbullin e hapur */
    public boolean discardCard(PeseqindshState state, PeseqindshPlayer player, Card card) {
        if (state.getCurrentPlayerSeat() != player.getSeatIndex()) {
            throw new IllegalStateException("Nuk është radha jote.");
        }
        if (state.isDiscardedThisTurn()) {
            throw new IllegalStateException("Ke hedhur tashmë një letër këtë radhë.");
        }
        if (!player.getHand().remove(card)) {
            throw new IllegalStateException("Nuk e ke këtë letër në dorë.");
        }
        state.getOpenPile().add(card);
        state.setDiscardedThisTurn(true);

        if (player.isHandEmpty()) {
            finishRound(state, player.getSeatIndex());
            return true; // raundi mbaroi menjëherë, s'ka nevojë për 'draw'
        }
        return false;
    }

    /** Hap 2 (variant A): tërheq 1 letër nga grumbulli i mbyllur, mbyll radhën */
    public void drawFromClosed(PeseqindshState state, PeseqindshPlayer player) {
        requireDiscardedFirst(state, player);
        if (state.getClosedPile().isEmpty()) {
            // Talon bosh -> barazim teknik i thjeshtë: rimbush nga openPile (pa letrën e fundit)
            reshuffleClosedFromOpen(state);
        }
        Card drawn = state.getClosedPile().poll();
        if (drawn != null) player.getHand().add(drawn);
        state.switchTurn();
    }

    /**
     * Hap 2 (variant B - vetëm nëse je i shtruar): merr GJITHË letrat e hapura.
     * Ato mbeten "pezull" (pendingForcedCards) derisa të përdoren 100% në kombinime.
     */
    public void takeOpenPile(PeseqindshState state, PeseqindshPlayer player) {
        requireDiscardedFirst(state, player);
        if (!player.isHasOpened()) {
            throw new IllegalStateException("Vetëm një lojtar i shtruar mund të marrë gjithë tokën.");
        }
        if (state.getOpenPile().isEmpty()) {
            throw new IllegalStateException("Grumbulli i hapur është bosh.");
        }
        List<Card> taken = new ArrayList<>(state.getOpenPile());
        state.getOpenPile().clear();
        player.getHand().addAll(taken);
        state.getPendingForcedCards().clear();
        state.getPendingForcedCards().addAll(taken);
        // Radha NUK kalon ende — pritet t'i përdorë të gjitha te addMeld/extendMeld,
        // pastaj thirret endForcedTurn().
    }

    /** Thirret pasi lojtari ka përdorur të gjitha letrat 'pending' nga takeOpenPile */
    public void endForcedTurn(PeseqindshState state, PeseqindshPlayer player) {
        if (!state.getPendingForcedCards().isEmpty()) {
            throw new IllegalStateException("Duhet të përdorësh të gjitha letrat e marra nga toka para se të vazhdosh.");
        }
        if (player.isHandEmpty()) {
            finishRound(state, player.getSeatIndex());
            return;
        }
        state.switchTurn();
    }

    private void requireDiscardedFirst(PeseqindshState state, PeseqindshPlayer player) {
        if (state.getCurrentPlayerSeat() != player.getSeatIndex()) {
            throw new IllegalStateException("Nuk është radha jote.");
        }
        if (!state.isDiscardedThisTurn()) {
            throw new IllegalStateException("Duhet të hedhësh një letër para se të tërheqësh.");
        }
    }

    private void reshuffleClosedFromOpen(PeseqindshState state) {
        if (state.getOpenPile().size() <= 1) return; // s'ka mjaftueshëm për rimbushje
        Card top = state.getOpenPile().remove(state.getOpenPile().size() - 1);
        List<Card> rest = new ArrayList<>(state.getOpenPile());
        Collections.shuffle(rest);
        state.getOpenPile().clear();
        state.getOpenPile().add(top);
        state.getClosedPile().addAll(rest);
    }

    // ============================================================
    //  MBYLLJA E RAUNDIT DHE PIKËT
    // ============================================================

    private void finishRound(PeseqindshState state, int winnerSeat) {
        PeseqindshPlayer winner = state.getPlayerBySeat(winnerSeat);
        PeseqindshPlayer loser = state.getOpponentOf(winnerSeat);

        int winnerPoints = state.meldsOwnedBy(winnerSeat).stream().mapToInt(this::meldPoints).sum();
        int loserHandPenalty = loser.getHand().stream().mapToInt(this::cardValue).sum();

        winner.addScore(winnerPoints);
        loser.addScore(-loserHandPenalty);

        state.setPhase(PeseqindshPhase.ROUND_FINISHED);

        if (winner.getTotalScore() >= PeseqindshState.TARGET_SCORE) {
            state.setPhase(PeseqindshPhase.GAME_OVER);
        }
    }

    /** Fillon raundin tjetër; fituesi i raundit të kaluar preu letrat (avantazhi i xhokerit) */
    public void startNextRound(PeseqindshState state, int previousWinnerSeat) {
        state.setRoundNumber(state.getRoundNumber() + 1);
        startNewRound(state, previousWinnerSeat);
    }
}
