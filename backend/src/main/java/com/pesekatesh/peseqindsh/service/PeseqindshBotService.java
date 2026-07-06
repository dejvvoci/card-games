package com.pesekatesh.peseqindsh.service;

import com.pesekatesh.model.Card;
import com.pesekatesh.peseqindsh.model.PeseqindshPlayer;
import com.pesekatesh.peseqindsh.model.PeseqindshState;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;

/**
 * Bot "pasiv" për Peseqindsh: nuk kërkon kombinime as nuk hapet (do kërkonte kërkim kombinimesh
 * shumë më kompleks se Kate 1-4), thjesht hedh letrën më të shtrenjtë të dorës dhe tërheq nga
 * grumbulli i mbyllur — mjafton për të zënë vendin bosh kur s'ka lojtar të vërtetë brenda 60s.
 */
@Service
public class PeseqindshBotService {

    /** Luan radhën e plotë të bot-it: hedh 1 letër, pastaj tërheq (ose raundi mbaron nëse dora bosh) */
    public void playTurn(PeseqindshState state, PeseqindshPlayer bot, PeseqindshService gameService) {
        Card toDiscard = chooseDiscard(bot, gameService);
        boolean roundFinished = gameService.discardCard(state, bot, toDiscard);
        if (roundFinished) return;
        gameService.drawFromClosed(state, bot);
    }

    /** Zgjedh letrën me vlerën më të lartë (kursen letrat e vogla, heq qafe "barrën" e pikëve) */
    private Card chooseDiscard(PeseqindshPlayer bot, PeseqindshService gameService) {
        return Collections.max(bot.getHand(), Comparator.comparingInt(gameService::cardValue));
    }
}
