package com.pesekatesh.peseqindsh.dto;

import com.pesekatesh.model.Card;
import com.pesekatesh.model.Suit;

public class CardDto {
    private String suit; // "MAC","ZEMER","ROMB","SPATHI"
    private int rank;

    public CardDto() {}
    public CardDto(Card c) { this.suit = c.getSuit().name(); this.rank = c.getRank(); }

    public String getSuit() { return suit; }
    public void setSuit(String suit) { this.suit = suit; }
    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public Card toCard() { return new Card(Suit.valueOf(suit), rank); }
}
