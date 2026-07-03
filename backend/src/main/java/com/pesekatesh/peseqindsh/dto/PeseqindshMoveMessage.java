package com.pesekatesh.peseqindsh.dto;

import java.util.List;

public class PeseqindshMoveMessage {
    private String playerId;

    // discardCard / extendMeld (letër e vetme)
    private CardDto card;

    // addMeld (një kombinim i vetëm)
    private List<CardDto> cards;

    // openHand (disa kombinime njëkohësisht)
    private List<List<CardDto>> meldGroups;

    // extendMeld: te cili kombinim po ngjitet letra
    private String meldId;

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public CardDto getCard() { return card; }
    public void setCard(CardDto card) { this.card = card; }
    public List<CardDto> getCards() { return cards; }
    public void setCards(List<CardDto> cards) { this.cards = cards; }
    public List<List<CardDto>> getMeldGroups() { return meldGroups; }
    public void setMeldGroups(List<List<CardDto>> meldGroups) { this.meldGroups = meldGroups; }
    public String getMeldId() { return meldId; }
    public void setMeldId(String meldId) { this.meldId = meldId; }
}
