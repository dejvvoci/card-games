package com.pesekatesh.service;

import com.pesekatesh.model.GameSession;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomManager {

    /** Regjistri qendror i të gjitha dhomave aktive, thread-safe për akses konkurrent nga WebSocket sessions */
    private final Map<String, GameSession> rooms = new ConcurrentHashMap<>();

    public GameSession getOrCreateRoom(String roomId, boolean soloMode, boolean shtatatEveryRound) {
        return rooms.computeIfAbsent(roomId, id -> new GameSession(id, soloMode, shtatatEveryRound));
    }

    public GameSession getRoom(String roomId) {
        return rooms.get(roomId);
    }

    public void removeRoom(String roomId) {
        rooms.remove(roomId);
    }

    public boolean roomExists(String roomId) {
        return rooms.containsKey(roomId);
    }
}
