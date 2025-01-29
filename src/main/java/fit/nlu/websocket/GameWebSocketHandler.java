package fit.nlu.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import fit.nlu.service.GameService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.logging.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    @Autowired
    private GameService gameService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SessionManager sessionManager;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//        log.info("New WebSocket connection: {}", session.getId());
        sessionManager.addSession(session.getId(), session);
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
//        log.info("Received message: {}", message.getPayload());
        try {
//            GameMessage gameMessage = objectMapper.readValue(message.getPayload(), GameMessage.class);
//            handleGameMessage(session, gameMessage);
        } catch (Exception e) {
//            log.error("Error handling message", e);
        }
    }

//    private void handleGameMessage(WebSocketSession session, GameMessage message) {
//        switch (message.getType()) {
//            case DRAWING:
//                gameService.handleDrawing(session, message);
//                break;
//            case GUESS:
//                gameService.handleGuess(session, message);
//                break;
//            case CHAT:
//                gameService.handleChat(session, message);
//                break;
//        }
//    }
}
