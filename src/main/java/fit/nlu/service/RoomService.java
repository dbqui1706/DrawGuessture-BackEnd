package fit.nlu.service;

import fit.nlu.dto.response.ListRoomResponse;
import fit.nlu.enums.RoomState;
import fit.nlu.exception.GameException;
import fit.nlu.model.Player;
import fit.nlu.model.Room;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RoomService {
    private static final Logger log = LoggerFactory.getLogger(RoomService.class);
    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate simpMessagingTemplate;


    // Tạo phòng mới
    public Room createRoom(Player owner) {
        Room room = new Room(owner);
        rooms.put(room.getId().toString(), room);
        owner.setRoomId(room.getId().toString());
        // Broadcast thông báo có phòng mới cho tất cả users
        broadcastRoomListUpdate();
        log.info("Created new room: {}", room.getId());
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
    public Room joinRoom(String roomId, Player player) {
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

        // Thông báo cho tất cả người chơi trong phòng về người chơi mới
        sendRoomUpdate(room);

        // Cập nhật danh sách phòng cho tất cả users
        broadcastRoomListUpdate();

        log.info("Player {} joined room {}", player.getId(), roomId);
        return room;
    }

    // Rời phòng với real-time notification
    public void leaveRoom(String roomId, Player player) {
        Room room = rooms.get(roomId);
        if (room == null) {
            log.error("Room not found: {}", roomId);
            throw new GameException("Phòng không tồn tại");
        }

        room.removePlayer(player.getId());
        player.setRoomId(null);

        // Nếu phòng trống, xóa phòng
        if (room.getPlayers().isEmpty()) {
            rooms.remove(roomId);
            log.info("Room {} removed as it's empty", roomId);
        } else if (room.getOwner().getId().equals(player.getId())) {
            // Nếu owner rời phòng, chọn owner mới
            Player newOwner = room.getPlayers().values().iterator().next();
            newOwner.setOwner(true);
            room.setOwner(newOwner);
            log.info("New owner {} for room {}", newOwner.getId(), roomId);
        }

        // Thông báo cập nhật cho những người còn lại trong phòng
        if (!room.getPlayers().isEmpty()) {
            sendRoomUpdate(room);
            // Notify cho các người chơi trong phòng là player này đã rời phòng
            simpMessagingTemplate.convertAndSend(
                    "/topic/room/" + roomId + "/leave",
                    player
            );
        }

        // Cập nhật danh sách phòng cho tất cả users
        broadcastRoomListUpdate();

        log.info("Player {} left room {}", player.getId(), roomId);
    }

    private List<ListRoomResponse> createRoomResponseList() {
        List<Room> roomList = List.copyOf(rooms.values());
        List<ListRoomResponse> listRoomResponses = new ArrayList<>();
        for (Room room : roomList) {
            ListRoomResponse currentRoom = new ListRoomResponse(
                    room.getId().toString(),
                    room.getId().toString().substring(0, 8),
                    room.getSetting().getMaxPlayer(),
                    room.getPlayers().size(),
                    room.getState()
            );
            listRoomResponses.add(currentRoom);
        }
        return listRoomResponses;
    }

    private void sendRoomUpdate(Room room) {
        simpMessagingTemplate.convertAndSend(
                "/topic/room/" + room.getId() + "/update",
                room
        );
    }

    private void broadcastRoomListUpdate() {
        List<ListRoomResponse> roomList = createRoomResponseList();
        simpMessagingTemplate.convertAndSend("/topic/rooms", roomList);
    }

    // Cập nhật trạng thái phòng với real-time notification
    public void updateRoomState(String roomId, RoomState newState) {
        Room room = rooms.get(roomId);
        if (room == null) {
            log.error("Room not found: {}", roomId);
            throw new GameException("Phòng không tồn tại");
        }

        room.setState(newState);

        // Thông báo cập nhật cho tất cả người trong phòng
        sendRoomUpdate(room);

        // Cập nhật danh sách phòng cho tất cả users
        broadcastRoomListUpdate();

        log.info("Room {} state updated to {}", roomId, newState);
    }
}
