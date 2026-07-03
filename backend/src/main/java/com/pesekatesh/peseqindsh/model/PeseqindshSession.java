package com.pesekatesh.peseqindsh.model;

public class PeseqindshSession {
    private final String roomId;
    private final PeseqindshState state;

    public PeseqindshSession(String roomId) {
        this.roomId = roomId;
        this.state = new PeseqindshState(roomId);
    }

    public String getRoomId() { return roomId; }
    public PeseqindshState getState() { return state; }
    public boolean isFull() { return state.getPlayers().size() >= 2; }
}
