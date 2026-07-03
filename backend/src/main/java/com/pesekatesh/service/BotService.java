package com.pesekatesh.service;

import com.pesekatesh.model.*;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Vendos lëvizjet e bot-eve. E gjithë logjika ekzekutohet në Backend
 * (siç kërkohet), kështu që klienti Angular thjesht merr update-e të gatshme.
 */
@Service
public class BotService {

    // ============================================================
    //  KATE 1-4
    // ============================================================

    /** Zgjedh letrën më të mirë për bot-in në trick-un aktual */
    public Card chooseCardKate1to4(GameState state, Player bot, GameService gameService) {
        Suit led = state.getLedSuit();
        List<Card> hand = bot.getHand();

        if (led == null) {
            // Bot-i hap dorën: hap me një letër "të sigurt" (jo Derr, jo Q, jo Kupë e lartë)
            return hand.stream()
                    .filter(c -> !c.isDerrMac() && !c.isQueen() && !c.isZemer())
                    .min(Comparator.comparingInt(Card::getRank))
                    .orElse(hand.stream().min(Comparator.comparingInt(Card::getRank)).orElseThrow());
        }

        List<Card> ledSuitCards = hand.stream().filter(c -> c.getSuit() == led).toList();

        if (!ledSuitCards.isEmpty()) {
            // Duhet të ndjekë shenjën: përpiqet të mos fitojë dorën nëse ka letra "minus" në lojë
            Card currentBest = highestInTrick(state, led);
            List<Card> losingOptions = ledSuitCards.stream()
                    .filter(c -> currentBest == null || c.getRank() < currentBest.getRank())
                    .toList();
            if (!losingOptions.isEmpty()) {
                // luaj më të madhen nga ato që ende humbasin (kursen letrat e vogla për më vonë)
                return Collections.max(losingOptions, Comparator.comparingInt(Card::getRank));
            }
            // detyrohet të fitojë: luaj më të voglën fituese
            return Collections.min(ledSuitCards, Comparator.comparingInt(Card::getRank));
        }

        // "Shpresë": nuk ka shenjën -> hidhet letra më e keqe (heq qafe pikët negative)
        return hand.stream()
                .filter(Card::isDerrMac).findFirst()
                .or(() -> hand.stream().filter(Card::isQueen)
                        .max(Comparator.comparingInt(Card::getRank)))
                .or(() -> hand.stream().filter(Card::isZemer)
                        .max(Comparator.comparingInt(Card::getRank)))
                .orElse(Collections.max(hand, Comparator.comparingInt(Card::getRank)));
    }

    private Card highestInTrick(GameState state, Suit led) {
        return state.getCurrentTrick().values().stream()
                .filter(c -> c.getSuit() == led)
                .max(Comparator.comparingInt(Card::getRank))
                .orElse(null);
    }

    // ============================================================
    //  KATI 5 : SHTATAT
    // ============================================================

    /** Zgjedh lëvizjen më strategjike te Shtatat: prioritizon shenjat ku ka më shumë letra "bllokuara" */
    public Optional<Card> chooseCardKati5(GameState state, Player bot, GameService gameService) {
        List<Card> valid = bot.getHand().stream()
                .filter(c -> gameService.isValidSevensMove(state, c))
                .toList();
        if (valid.isEmpty()) return Optional.empty();

        // Preferon të luajë në shenjën ku vetë ka më shumë letra në pritje (çliron dorën më shpejt)
        Map<Suit, Long> countBySuit = new EnumMap<>(Suit.class);
        for (Card c : bot.getHand()) {
            countBySuit.merge(c.getSuit(), 1L, Long::sum);
        }
        return valid.stream()
                .max(Comparator.comparingLong(c -> countBySuit.getOrDefault(c.getSuit(), 0L)));
    }

    /** Zgjedh letrën "më pak të dobishme" për t'ia dhënë lojtarit të bllokuar */
    public Card chooseCardToGiveWhenBlocked(GameState state, Player giver) {
        // Preferon letrat ekstreme (2 ose Ace) sepse kanë më pak shanse zgjerimi në të ardhmen
        return giver.getHand().stream()
                .max(Comparator.comparingInt(c -> extremityScore(c)))
                .orElseThrow();
    }

    private int extremityScore(Card c) {
        // sa më larg mesit (rank 8), aq më "e panevojshme" konsiderohet
        return Math.abs(c.getRank() - 8);
    }
}
