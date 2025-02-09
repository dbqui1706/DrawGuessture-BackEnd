package fit.nlu.service;

import fit.nlu.dto.response.ListRoomResponse;
import fit.nlu.model.Message;
import fit.nlu.model.Player;
import fit.nlu.model.Room;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomEventNotifier {
    private final SimpMessagingTemplate simpMessagingTemplate;

    void sendRoomJoin(Room room, Player player) {
        simpMessagingTemplate.convertAndSend(
                "/topic/room/" + room.getId() + "/join",
                player
        );
    }

    void sendRoomLeave(Room room, Player player) {
        simpMessagingTemplate.convertAndSend(
                "/topic/room/" + room.getId() + "/leave",
                player
        );
    }

    void sendRoomUpdate(Room room) {
        simpMessagingTemplate.convertAndSend(
                "/topic/room/" + room.getId() + "/update",
                room
        );
    }

    void broadcastMessage(String roomId, Message message) {
        simpMessagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/message",
                message
        );
    }

    void broadcastRoomListUpdate(List<ListRoomResponse> roomList) {
        simpMessagingTemplate.convertAndSend("/topic/rooms", roomList);
    }
}
