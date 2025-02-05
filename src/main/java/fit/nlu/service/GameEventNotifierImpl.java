package fit.nlu.service;

import fit.nlu.dto.response.TurnDto;
import fit.nlu.enums.MessageType;
import fit.nlu.model.Message;
import fit.nlu.model.Turn;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GameEventNotifierImpl implements GameEventNotifier {
    private static final Logger log = LoggerFactory.getLogger(GameEventNotifierImpl.class);
    private final SimpMessagingTemplate simpMessagingTemplate;

    @Override
    public void notifyGameStart(String roomId) {
        Message message = new Message();
        message.setType(MessageType.GAME_START);
        message.setContent("Game bắt đầu");
        simpMessagingTemplate.convertAndSend("/topic/room/" + roomId + "/message", message);
        log.info("Notified game start for room: {}", roomId);
    }

    @Override
    public void notifyRoundStart(String roomId, int roundNumber) {
        Message message = new Message();
        message.setType(MessageType.ROUND_START);
        message.setContent("Vòng " + roundNumber + " bắt đầu");
        simpMessagingTemplate.convertAndSend("/topic/room/" + roomId + "/message", message);
        log.info("Notified round {} start for room {}", roundNumber, roomId);
    }

    @Override
    public void notifyTurnStart(String roomId, Turn turn) {
        TurnDto turnDto = new TurnDto();
        turnDto.setTurnId(turn.getId());
        turnDto.setDrawer(turn.getDrawer());
        turnDto.setTimeLimit(turn.getTimeLimit());
        turnDto.setKeyword(turn.getKeyword()); // Cho người vẽ biết keyword
        turnDto.setEventType("TURN_START");

        String drawerName = turn.getDrawer().getNickname();
        Message message = new Message();
        message.setSender(turn.getDrawer());
        message.setType(MessageType.TURN_START);
        message.setContent("Lượt vẽ của " + drawerName);

        simpMessagingTemplate.convertAndSend("/topic/room/" + roomId + "/message", message);
        simpMessagingTemplate.convertAndSend("/topic/room/" + roomId + "/turn", turnDto);
        log.info("Notified turn start for drawer {} in room {}", drawerName, roomId);
    }

    @Override
    public void notifyTurnEnd(String roomId, Turn turn) {
        String drawerName = turn.getDrawer().getNickname();
        Message message = new Message();
        message.setType(MessageType.TURN_END);
        message.setContent("Lượt của " + drawerName + " kết thúc");

        simpMessagingTemplate.convertAndSend("/topic/room/" + roomId + "/message", message);
        log.info("Notified turn end for drawer {} in room {}", drawerName, roomId);
    }

    @Override
    public void notifyRoundEnd(String roomId, int roundNumber) {
        Message message = new Message();
        message.setType(MessageType.ROUND_END);
        message.setContent("Vòng " + roundNumber + " kết thúc");

        simpMessagingTemplate.convertAndSend("/topic/room/" + roomId + "/message", message);
        log.info("Notified round {} end for room {}", roundNumber, roomId);
    }

    @Override
    public void notifyGameEnd(String roomId) {
        Message message = new Message();
        message.setType(MessageType.GAME_END);
        message.setContent("Game kết thúc");
        simpMessagingTemplate.convertAndSend("/topic/room/" + roomId + "/message", message);
        log.info("Notified game end for room: {}", roomId);
    }
}
