package com.pesekatesh.model;

public class GameSession {

    private final String roomId;
    private final GameState state;
    private boolean soloMode;

    public GameSession(String roomId, boolean soloMode, boolean shtatatEveryRound) {
        this.roomId = roomId;
        this.soloMode = soloMode;
        this.state = new GameState(roomId);
        this.state.setShtatatEveryRound(shtatatEveryRound);
    }

    public String getRoomId() { return roomId; }
    public GameState getState() { return state; }
    public boolean isSoloMode() { return soloMode; }
    public boolean isFull() { return state.getPlayers().size() >= 4; }
}
