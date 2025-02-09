package fit.nlu.service;

import fit.nlu.dto.response.ListRoomResponse;
import fit.nlu.enums.MessageType;
import fit.nlu.enums.RoomState;
import fit.nlu.exception.GameException;
import fit.nlu.model.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomService {
    private static final Logger log = LoggerFactory.getLogger(RoomService.class);
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final @Lazy GameEventNotifier notifier;
    private final ScheduledThreadPoolExecutor executorService;
    private final RoomEventNotifier roomEventNotifier;

    // Tạo phòng mới
    public Room createRoom(Player owner) {
        Room room = new Room(owner);
        rooms.put(room.getId().toString(), room);
        owner.setOwner(true);
        owner.setRoomId(room.getId().toString());
        // Broadcast thông báo có phòng mới cho tất cả users
        roomEventNotifier.broadcastRoomListUpdate(createRoomResponseList());
        log.info("Created new room: {}", room.getId());


        // Notify tin nhắn Player nào là chủ phòng
        Message message = new Message();
        message.setType(MessageType.CREATE_ROOM);
        message.setSender(owner);
        roomEventNotifier.sendRoomUpdate(room);
        roomEventNotifier.broadcastMessage(room.getId().toString(), message);
        return room;
    }

    // Lấy danh sách room hiện có
    public List<ListRoomResponse> getRooms() {
        if (rooms.isEmpty()) {
            log.info("No room found");
            return List.of();
        }
        return createRoomResponseList();
    }

    // Tham gia phòng với real-time notification
    public synchronized Room joinRoom(String roomId, Player player) {
        Room room = rooms.get(roomId);
        if (room == null) {
            log.error("Room not found: {}", roomId);
            throw new GameException("Phòng không tồn tại");
        }

        if (room.getPlayers().size() >= room.getSetting().getMaxPlayer()) {
            log.error("Room is full: {}", roomId);
            throw new GameException("Phòng đã đầy");
        }
        room.addPlayer(player);
        player.setRoomId(roomId);

        // Nếu game đã bắt đầu, cập nhật Round hiện tại
        if (room.getState() != RoomState.WAITING && room.getGameSession() != null) {
            Round currentRound = room.getGameSession().getCurrentRound();
            if (currentRound != null && currentRound.getRemainingPlayers() != null) {
                currentRound.addPlayer(player);
            }
        }

        // THông báo cho mọi người trong phòng một message là player này đã tham gia phòng
        Message message = new Message();
        message.setType(MessageType.PLAYER_JOIN);
        message.setSender(player);
        roomEventNotifier.broadcastMessage(roomId, message);
        // Thông báo cập nhật phòng cho tất cả mọi người
        roomEventNotifier.sendRoomUpdate(room);

        // Cập nhật danh sách phòng cho tất cả users
        roomEventNotifier.broadcastRoomListUpdate(createRoomResponseList());

        log.info("Player {} joined room {}", player.getNickname(), roomId);
        return room;
    }

    // Rời phòng với real-time notification
    public synchronized void leaveRoom(String roomId, Player player) {
        Room room = getRoomOrThrow(roomId);
        // thông báo cho người chơi rời phòng ngay lập tức
        roomEventNotifier.sendRoomLeave(room, player);

        // Xử lý rời phòng nếu game đang diễn ra
        handleGameStateIfPlaying(room, player);

        room.removePlayer(player.getId());
        player.setRoomId(null);

        // Xử lý trạng thái phòng sau khi rời phòng
        handleRoomStateAfterLeaving(room, player);

        // Thông báo cập nhật cho những người còn lại trong phòng
        notifyPlayersAndBroadcast(room, player);
    }

    private void notifyPlayersAndBroadcast(Room room, Player player) {
        if (!room.getPlayers().isEmpty()) {

            roomEventNotifier.sendRoomUpdate(room);

            Message message = new Message();
            message.setType(MessageType.PLAYER_LEAVE);
            message.setSender(player);
            roomEventNotifier.broadcastMessage(room.getId().toString(), message);
        }

        roomEventNotifier.broadcastRoomListUpdate(createRoomResponseList());
        log.info("Player {} left room {}", player.getId(), room.getId());
    }

    private void handleRoomStateAfterLeaving(Room room, Player player) {
        if (room.getPlayers().isEmpty()) {
            removeRoom(room);
        } else {
            if (room.getOwner().getId().equals(player.getId())) {
                assignNewOwner(room);
            }
            // Nếu chỉ còn 1 người chơi thì kết thúc game chuyển về trạng thái chờ
            if (room.getPlayers().size() == 1) {
                handleSinglePlayerLeft(room);
            }
        }
    }

    private void assignNewOwner(Room room) {
        Player newOwner = room.getPlayers().values().iterator().next();
        newOwner.setOwner(true);
        room.setOwner(newOwner);

        Message message = new Message();
        message.setType(MessageType.UPDATE_OWNER);
        message.setSender(newOwner);

        roomEventNotifier.broadcastMessage(room.getId().toString(), message);
        log.info("New owner {} for room {}", newOwner.getId(), room.getId());
    }

    private void handleSinglePlayerLeft(Room room) {
        room.setState(RoomState.WAITING);
        Player newOwner = room.getPlayers().values().iterator().next();
        newOwner.setOwner(true);
        room.endGameSession();
    }

    /**
     * - Nếu hàm room.getPlayers() đang trong quá trình cập nhật (người chơi rời đi nhưng chưa xóa xong),
     * có thể xảy ra lỗi khi kiểm tra .isEmpty().
     * - Nếu server xử lý nhiều request cùng lúc, có thể có trường hợp hiếm xảy ra:
     * + Người cuối cùng rời phòng → rooms.remove(roomId)
     * + Một người mới vừa kịp tham gia lại phòng đó (trước khi nó bị xóa),
     * dẫn đến lỗi NullPointerException khi thao tác trên roomId.
     */
    private void removeRoom(Room room) {
        executorService.schedule(() -> {
            if (room.getPlayers().isEmpty()) {
                rooms.remove(room.getId().toString());
                log.info("Room {} removed as it's empty", room.getId());
            }
        }, 500, TimeUnit.MILLISECONDS);
    }

    private void handleGameStateIfPlaying(Room room, Player player) {
        if (room.getState() == RoomState.WAITING || room.getGameSession() == null) {
            return;
        }

        Round currentRound = room.getGameSession().getCurrentRound();
        if (currentRound == null) {
            return;
        }
        // Xóa người chơi khỏi round nếu họ còn tồn tại
        currentRound.removePlayer(player);

        Turn currentTurn = currentRound.getCurrentTurn();

        // Nếu không phải lượt của người rời phòng thì không cần xử lý
        if (currentTurn == null || !currentTurn.getDrawer().getId().equals(player.getId())) return;

        // Nếu là lượt của người rời phòng
        // 1. Kết thúc lượt vẽ
        // 2. Chuyển lượt vẽ cho người tiếp theo
        currentTurn.completedTurn();
        notifier.notifyTurnEnd(room.getId().toString(), currentTurn);

        // Chuyển lượt vẽ cho người tiếp theo
        currentRound.nextTurn(() -> {
            if (currentRound.getRemainingPlayers().isEmpty()) {
                currentRound.endRound();
                notifier.notifyRoundEnd(room.getId().toString(), currentRound.getRoundNumber());
            }
        });

    }

    // Lấy phòng hoặc ném ra ngoại lệ nếu không tìm thấy
    private Room getRoomOrThrow(String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            log.error("Room not found: {}", roomId);
            throw new GameException("Phòng không tồn tại");
        }
        return room;
    }

    // Bắt đầu game với real-time notification
    public void startGame(String roomId) {
        Room room = rooms.get(roomId);
        if (room == null) {
            log.error("Room not found: {}", roomId);
            throw new GameException("Phòng không tồn tại");
        }

        if (room.getPlayers().size() < 2) {
            log.error("Not enough players to start the game in room: {}", roomId);
            throw new GameException("Phải có ít nhất 2 người chơi để bắt đầu game");
        }

        if (room.getState() != RoomState.WAITING) {
            log.error("Game has already started in room: {}", roomId);
            throw new GameException("Game đã bắt đầu");
        }

        room.setState(RoomState.PLAYING);

        // Gọi hàm startGameSession, truyền notifier để game logic gửi thông báo sau này.
        room.startGameSession(notifier);

        log.info("Game started in room: {}", roomId);
    }

    public boolean addDrawingData(String roomId, DrawingData data) {
        Room room = rooms.get(roomId);
        if (room == null) {
            log.error("Room not found: {}", roomId);
            return false;
        }
        GameSession gameSession = room.getGameSession();
        if (gameSession == null) return false;

        Turn currentTurn = gameSession.getCurrentTurn();
        if (currentTurn == null) return false;
        currentTurn.addDrawingData(data);
        return true;
    }

    public RoomSetting updateRoomOptions(String roomId, RoomSetting newSetting) {
        Room room = rooms.get(roomId);
        if (room == null) {
            log.error("Room not found: {}", roomId);
            throw new GameException("Phòng không tồn tại");
        }

        room.setSetting(newSetting);
        log.info("Room {} options updated to {}", roomId, newSetting);

        // Thông báo cập nhật cho tất cả người trên server
        roomEventNotifier.broadcastRoomListUpdate(createRoomResponseList());
        return newSetting;
    }

    private List<ListRoomResponse> createRoomResponseList() {
        List<Room> roomList = List.copyOf(rooms.values());
        List<ListRoomResponse> listRoomResponses = new ArrayList<>();
        for (Room room : roomList) {
            ListRoomResponse currentRoom = new ListRoomResponse(
                    room.getId().toString(),
                    room.getId().toString().substring(0, 5),
                    room.getSetting().getMaxPlayer(),
                    room.getPlayers().size(),
                    room.getState()
            );
            listRoomResponses.add(currentRoom);
        }
        return listRoomResponses;
    }

    public synchronized Room getRoomById(String roomId) {
        return rooms.get(roomId);
    }

}
