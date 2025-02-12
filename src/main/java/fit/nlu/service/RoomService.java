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

        // Nếu game đã bắt đầu, cập nhật Round và Turn hiện tại
        if (room.getState() != RoomState.WAITING && room.getGameSession() != null) {
            Round currentRound = room.getGameSession().getCurrentRound();
            if (currentRound != null && currentRound.getRemainingPlayers() != null) {
                currentRound.addPlayer(player);
                Turn currentTurn = currentRound.getCurrentTurn();
                if (currentTurn != null) {
                    currentTurn.addPlayer(player);
                }
            }
        }

        // THông báo cho mọi người trong phòng một message là player này đã tham gia phòng
        Message message = new Message();
        message.setType(MessageType.PLAYER_JOIN);
        message.setSender(player);

        roomEventNotifier.broadcastMessage(roomId, message);
        // Thông báo cập nhật phòng cho tất cả mọi người
        roomEventNotifier.sendRoomJoin(room, player);

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
        roomEventNotifier.sendRoomUpdate(room);
        notifyPlayersAndBroadcast(room, player);
    }

    private void notifyPlayersAndBroadcast(Room room, Player player) {
        if (!room.getPlayers().isEmpty()) {
            Message message = new Message();
            message.setType(MessageType.PLAYER_LEAVE);
            message.setSender(player);
            roomEventNotifier.broadcastMessage(room.getId().toString(), message);
        }
        log.info("notifyPlayersAndBroadcast room state {} and Player {}", room.getState(), room.getCurrentPlayers().size());
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
                // Kết thúc game nếu đang chơi
                room.endGameSession();
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
        log.info("Room {} has only 1 player left", room.getId());
        // Chuyển trạng thái phòng về chờ
        room.setState(RoomState.WAITING);
        // Set drawing và score cho người chơi còn lại về false và 0
        room.getPlayers().values().forEach(player -> {
            player.setDrawing(false);
            player.setScore(0);
        });

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
        if (room.getPlayers().size() < 2) {
            room.setState(RoomState.WAITING);
            room.endGameSession();
            return;
        }

        Round currentRound = room.getGameSession().getCurrentRound();
        if (currentRound == null) {
            return;
        }

        // Xóa người chơi khỏi round nếu họ còn tồn tại
        currentRound.removePlayer(player);

        Turn currentTurn = currentRound.getCurrentTurn();
        if (currentTurn == null) return;

        currentTurn.removePlayer(player);

        // Nếu người rời phòng không phải là người vẽ, không cần thay đổi lượt vẽ
        if (!currentTurn.getDrawer().getId().equals(player.getId())) return;

        // Nếu là người vẽ -> Kết thúc lượt vẽ & hiển thị kết quả
        currentTurn.completedTurn(() -> {
            currentTurn.startThreadShowResultTurn(() -> {
                // Khi kết quả hiển thị xong -> Bắt đầu lượt vẽ mới
                currentRound.nextTurn(() -> {
                    if (currentRound.getRemainingPlayers().isEmpty()) {
                        currentRound.endRound();
                        notifier.notifyRoundEnd(room.getId().toString(), currentRound.getRoundNumber());
                    }
                });
            });
        });

        notifier.notifyTurnEnd(room.getId().toString(), currentTurn);
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

    public Message chatMessage(String roomId, Message message) {
        // 1. Lấy phòng và kiểm tra trạng thái
        Room room = getRoomOrThrow(roomId);
        if (room.getState() != RoomState.PLAYING) {
            message.setContent(message.getSender().getNickname() + ": " + message.getContent());
            return message;
        }

        GameSession gameSession = room.getGameSession();
        if (gameSession == null) return message;

        Turn currentTurn = gameSession.getCurrentTurn();
        if (currentTurn == null) return message;

        // 2. Lấy thông tin người gửi, người vẽ và keyword của lượt hiện tại
        Player sender = message.getSender();
        Player drawer = currentTurn.getDrawer();

        String keyword = currentTurn.getKeyword().trim().toLowerCase();
        String content = message.getContent().trim();
        String contentLower = content.toLowerCase();

        boolean isExactMatch = contentLower.equals(keyword);
        boolean containsKeyword = contentLower.contains(keyword);

        // 3. Xử lý đối với người vẽ (drawer)
        if (sender.getId().equals(drawer.getId())) {
            // Nếu người vẽ gửi bất kỳ tin nhắn nào chứa keyword (exact hoặc chứa), xóa nội dung tin nhắn.
            if (containsKeyword) {
                message.setContent(drawer.getNickname() + ": đã cố tình gian lận");
            } else {
                message.setContent(drawer.getNickname() + ": " + message.getContent());
            }
            return message;
        }

        // 4. Xử lý đối với người đoán (guesser)
        if (isExactMatch) {
            // Tin nhắn chính xác bằng keyword.
            boolean alreadyGuessed = currentTurn.getGuesses().stream()
                    .anyMatch(guess -> guess.getPlayer().getId().equals(sender.getId()));
            if (!alreadyGuessed) {
                // Nếu chưa tồn tại người chơi đoán đúng, thêm vào danh sách đoán đúng.
                currentTurn.submitGuess(new Guess(sender, content));
                message.setContent(sender.getNickname() + " đã đoán đúng từ khóa");
                return message;
            } else if (containsKeyword && alreadyGuessed) {
                // Tin nhắn không phải exact match mà chỉ chứa keyword → xem là gian lận.
                message.setContent(sender.getNickname() + ": đã cố tình gian lận");
            }
        }
        // Nếu tin nhắn không chứa keyword thì giữ nguyên nội dung tin nhắn (chat bình thường)
        message.setContent(sender.getNickname() + ": " + message.getContent());
        return message;
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
    public synchronized boolean clearRooms() {
        rooms.clear();
        return rooms.isEmpty();
    }
}
