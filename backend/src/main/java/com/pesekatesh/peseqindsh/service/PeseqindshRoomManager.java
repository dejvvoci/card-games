package com.pesekatesh.peseqindsh.service;

import com.pesekatesh.peseqindsh.model.PeseqindshSession;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PeseqindshRoomManager {

    private final Map<String, PeseqindshSession> rooms = new ConcurrentHashMap<>();

    public PeseqindshSession getOrCreateRoom(String roomId) {
        return rooms.computeIfAbsent(roomId, PeseqindshSession::new);
    }

    public PeseqindshSession getRoom(String roomId) { return rooms.get(roomId); }
    public void removeRoom(String roomId) { rooms.remove(roomId); }
}
