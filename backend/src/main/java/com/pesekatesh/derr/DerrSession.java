package com.pesekatesh.derr;

public class DerrSession {
    private final String roomId;
    private final DerrState state;

    public DerrSession(String roomId) {
        this.roomId = roomId;
        this.state = new DerrState(roomId);
    }

    public String getRoomId() { return roomId; }
    public DerrState getState() { return state; }
    public boolean isFull() { return state.getPlayers().size() >= DerrState.SEAT_COUNT; }
}
