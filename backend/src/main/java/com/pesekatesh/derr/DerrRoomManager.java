package com.pesekatesh.derr;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DerrRoomManager {

    private final Map<String, DerrSession> rooms = new ConcurrentHashMap<>();

    public DerrSession getOrCreateRoom(String roomId) {
        return rooms.computeIfAbsent(roomId, DerrSession::new);
    }

    public DerrSession getRoom(String roomId) { return rooms.get(roomId); }
    public void removeRoom(String roomId) { rooms.remove(roomId); }
}
