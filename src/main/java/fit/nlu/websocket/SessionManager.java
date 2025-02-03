package fit.nlu.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


@Component
public class SessionManager {
    private static final Logger log = LoggerFactory.getLogger(SessionManager.class);

    // Map lưu trữ tất cả các sessions
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // Map lưu trữ sessions theo phòng
    private final ConcurrentHashMap<String, Set<String>> roomSessions = new ConcurrentHashMap<>();

    // Thêm session mới
    public void addSession(String sessionId, WebSocketSession session) {
        sessions.put(sessionId, session);
        log.info("Added new session: {}", sessionId);
    }

    // Thêm session vào phòng
    public void addToRoom(String roomId, String sessionId) {
        roomSessions.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
                .add(sessionId);
        log.info("Added session {} to room {}", sessionId, roomId);
    }

    // Xóa session khỏi phòng
    public void removeFromRoom(String roomId, String sessionId) {
        Set<String> roomSessionIds = roomSessions.get(roomId);
        if (roomSessionIds != null) {
            roomSessionIds.remove(sessionId);
            if (roomSessionIds.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }
        log.info("Removed session {} from room {}", sessionId, roomId);
    }

    // Xóa session
    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
        // Xóa session khỏi tất cả các phòng
        for (Set<String> roomSessionIds : roomSessions.values()) {
            roomSessionIds.remove(sessionId);
        }
        log.info("Removed session: {}", sessionId);
    }

}
