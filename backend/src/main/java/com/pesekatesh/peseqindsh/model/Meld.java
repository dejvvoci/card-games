package com.pesekatesh.peseqindsh.model;

import com.pesekatesh.model.Card;
import com.pesekatesh.model.Suit;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Meld {

    private final String id = UUID.randomUUID().toString();
    private MeldType type;
    private Suit suit;              // vetëm për RUN (të gjitha letrat duhet të jenë kësaj lule)
    private Integer rank;           // vetëm për SET (vlera e përbashkët)
    private int ownerSeat;
    private List<Card> cards = new ArrayList<>();

    public Meld() {}

    public Meld(MeldType type, int ownerSeat) {
        this.type = type;
        this.ownerSeat = ownerSeat;
    }

    public String getId() { return id; }
    public MeldType getType() { return type; }
    public void setType(MeldType type) { this.type = type; }
    public Suit getSuit() { return suit; }
    public void setSuit(Suit suit) { this.suit = suit; }
    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }
    public int getOwnerSeat() { return ownerSeat; }
    public List<Card> getCards() { return cards; }

    public long countJokers() {
        return cards.stream().filter(c -> c.getRank() == 2).count();
    }
}
