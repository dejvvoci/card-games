package com.pesekatesh.model;

import java.util.Objects;

public class Card {

    private final Suit suit;
    /** 2..14  (11=J, 12=Q, 13=K, 14=Ace) — rendi zyrtar "nga 2 te Asi" */
    private final int rank;

    public Card(Suit suit, int rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public Suit getSuit() { return suit; }
    public int getRank() { return rank; }

    public boolean isDerrMac() {
        return suit == Suit.MAC && rank == 13; // King i Maçit
    }

    public boolean isQueen() {
        return rank == 12;
    }

    public boolean isZemer() {
        return suit == Suit.ZEMER;
    }

    public boolean isSeventOfSpathi() {
        return suit == Suit.SPATHI && rank == 7;
    }

    public boolean isSeven() {
        return rank == 7;
    }

    /** Krahasim vetëm brenda të njëjtës shenjë (për trick 1-4) */
    public boolean beats(Card other) {
        if (this.suit != other.suit) return false;
        return this.rank > other.rank;
    }

    public static String rankLabel(int rank) {
        return switch (rank) {
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "A";
            default -> String.valueOf(rank);
        };
    }

    @Override
    public String toString() {
        return rankLabel(rank) + suit.symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Card card)) return false;
        return rank == card.rank && suit == card.suit;
    }

    @Override
    public int hashCode() { return Objects.hash(suit, rank); }
}
