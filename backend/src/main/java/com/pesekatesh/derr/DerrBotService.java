package com.pesekatesh.derr;

import org.springframework.stereotype.Service;

import java.util.Random;

/** Bot-i thjesht tërheq një pozicion të rastësishëm nga dora e verbër e holder-it — s'ka asnjë informacion tjetër për të vendosur ndryshe */
@Service
public class DerrBotService {

    private final Random random = new Random();

    public int chooseCardIndex(DerrState state) {
        int holderHandSize = state.getHolder().getHand().size();
        return random.nextInt(holderHandSize);
    }
}
