package fit.nlu.service;

import fit.nlu.dto.request.CreateRoomRequest;
import fit.nlu.dto.response.ListRoomResponse;
import fit.nlu.model.ChatSystem;
import fit.nlu.model.Player;
import fit.nlu.model.Room;
import fit.nlu.websocket.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {
    private static final Logger log = LoggerFactory.getLogger(RoomService.class);
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private SessionManager sessionManager;

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    public Room createRoom(Player owner) {
        Room room = new Room(owner);
        rooms.put(room.getId().toString(), room);
        owner.setRoomId(room.getId().toString());
        return room;
    }

    // Lấy danh sách room trong Redis
    public List<ListRoomResponse> getRooms() {
        if (rooms.isEmpty()) {
            log.info("No room found");
            return List.of();
        }
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

    public Room joinRoom(String roomId, Player player) {
        Room room = rooms.get(roomId);
        if (room == null) {
            log.error("Room not found: {}", roomId);
            return null;
        }
        room.addPlayer(player);
        return room;

    }
}
