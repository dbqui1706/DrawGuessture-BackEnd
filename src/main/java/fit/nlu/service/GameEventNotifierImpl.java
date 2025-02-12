package fit.nlu.service;

import fit.nlu.enums.MessageType;
import fit.nlu.enums.RoomState;
import fit.nlu.model.Message;
import fit.nlu.model.Room;
import fit.nlu.model.Turn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class GameEventNotifierImpl implements GameEventNotifier {
    private static final Logger log = LoggerFactory.getLogger(GameEventNotifierImpl.class);
    private final SimpMessagingTemplate simpMessagingTemplate;
    private RoomService roomService;

    public GameEventNotifierImpl(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @Lazy
    @Autowired
    public void setRoomService(RoomService roomService) {
        this.roomService = roomService;
    }

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
    public synchronized void notifyTurnStart(String roomId, Turn turn) {
        String drawerName = turn.getDrawer().getNickname();
        Message message = new Message();
        message.setSender(turn.getDrawer());
        message.setType(MessageType.TURN_START);
        message.setContent("Lượt vẽ của " + drawerName);

        simpMessagingTemplate.convertAndSend("/topic/room/" + roomId + "/message", message);

        Room room = roomService.getRoomById(roomId);
        room.setState(RoomState.PLAYING);
        // Cập nhập danh sách Player nào đang vẽ
        room.updatePlayerDrawing(turn.getDrawer().getId());

        if (room == null) {
            log.error("Room {} not found", roomId);
            return;
        } else {
            // Gửi Turn để bên client cập nhật thông tin
            simpMessagingTemplate.convertAndSend("/topic/room/" + roomId + "/turn", turn);
            log.info("Notified room update for room {} with turn start", roomId);
        }

        log.info("Notified turn start for drawer {} in room {}", drawerName, roomId);
    }

    @Override
    public synchronized void notifyTurnEnd(String roomId, Turn turn) {
        String drawerName = turn.getDrawer().getNickname();
        Message message = new Message();
        message.setType(MessageType.TURN_END);
        message.setContent("Lượt của " + drawerName + " kết thúc");

        Room room = roomService.getRoomById(roomId);
        // Update score for players
        room.updateScoreForPlayers(turn.getCurrentPlayers());
        room.setState(RoomState.TURN_TIMEOUT);

        simpMessagingTemplate.convertAndSend("/topic/room/" + roomId + "/turn-end", turn);

        simpMessagingTemplate.convertAndSend("/topic/room/" + roomId + "/score", room.getCurrentPlayers());

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

    @Override
    public void notifyTurnTimeUpdate(String roomId, Turn turn) {
        Message message = new Message();
        message.setType(MessageType.TIME_TURN_UPDATE);
        message.setContent(String.valueOf(turn.getServerRemainingTime()));

        simpMessagingTemplate.convertAndSend("/topic/room/" + roomId + "/time", message);
    }

    @Override
    public void notifyTurnResultCountdown(String roomId, Turn turn) {
        Message message = new Message();
        message.setType(MessageType.TIME_TURN_UPDATE);
        message.setContent(String.valueOf(turn.getServerRemainingTimeShowResult()));

        simpMessagingTemplate.convertAndSend("/topic/room/" + roomId + "/timeout", message);
    }
}
