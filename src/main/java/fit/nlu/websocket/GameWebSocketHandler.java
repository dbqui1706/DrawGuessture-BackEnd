package fit.nlu.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;
import java.util.Collections;

@Slf4j
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    List<WebSocketSession> webSocketSessions = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        log.info("New session connected: {}", session.getId());

        // Add new session to the list
        webSocketSessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        super.afterConnectionClosed(session, status);
        log.info("Session disconnected: {}", session.getId());

        // Remove session from the list
        webSocketSessions.remove(session);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        super.handleMessage(session, message);
        log.info("Received message from session {}: {}", session.getId(), message.getPayload());
        for (WebSocketSession webSocketSession : webSocketSessions) {
            if (session == webSocketSession) continue;
            webSocketSession.sendMessage(message);
        }
    }
}
