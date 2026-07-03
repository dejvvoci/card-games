package com.pesekatesh.model;

/**
 * 4 shenjat e letrave shqip:
 * MAC   = Spades   (♠)  -> përmban "Derr Maç" (K♠)
 * ZEMER = Hearts   (♥)  -> çdo letër = -2 pikë
 * ROMB  = Diamonds (♦)
 * SPATHI= Clubs    (♣)  -> 7♣ hap Katin e 5-të (Shtatat)
 */
public enum Suit {
    MAC("♠"), ZEMER("♥"), ROMB("♦"), SPATHI("♣");

    public final String symbol;
    Suit(String symbol) { this.symbol = symbol; }
}
