package fit.nlu.controller;

import fit.nlu.dto.request.CreateRoomRequest;
import fit.nlu.dto.request.JoinRoomRequest;
import fit.nlu.dto.response.ListRoomResponse;
import fit.nlu.exception.GameException;
import fit.nlu.model.Player;
import fit.nlu.model.Room;
import fit.nlu.model.RoomSetting;
import fit.nlu.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin("*")
public class GameController {
    private static final Logger log = LoggerFactory.getLogger(GameController.class);
    private final RoomService roomService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/room/{roomId}/options")
    @SendTo("/topic/room/{roomId}/options")
    public RoomSetting handleRoomOptions(
        @DestinationVariable String roomId,
        @Payload RoomSetting settings
    ) {
        // Log the incoming request
        log.info("Received room options update for room {}: {}", roomId, settings);
        try {
            return roomService.updateRoomOptions(roomId, settings);
        } catch (Exception e) {
            log.error("Error updating room options: ", e);
            return null;
        }
    }


    @MessageMapping("/room.create")
    @SendTo("/topic/room.create")
    public Room handleCreateRoom(@Payload Player owner) {
        System.out.println("Received create room request from player: " + owner.getId());
        log.info("Received create room request from player: {}", owner.getId());
        try {
            return roomService.createRoom(owner);
        } catch (Exception e) {
            log.error("Error creating room: ", e);
            // Gửi thông báo lỗi về cho client gốc
            simpMessagingTemplate.convertAndSendToUser(
                    owner.getId().toString(),
                    "/queue/errors",
                    new GameException("Không thể tạo phòng: " + e.getMessage())
            );
            return null;
        }
    }

    @MessageMapping("/room.join")
//    @SendTo("/topic/rooms")
    public Room handleJoinRoom(@Payload JoinRoomRequest joinRoomRequest) {
        log.info("Received join room request: room={}, player={}",
                joinRoomRequest.getRoomId(), joinRoomRequest.getPlayer().getId());
        try {
            return roomService.joinRoom(joinRoomRequest.getRoomId(), joinRoomRequest.getPlayer());
            // RoomService sẽ tự handle việc gửi updates
        } catch (Exception e) {
            log.error("Error joining room: ", e);
            simpMessagingTemplate.convertAndSendToUser(
                    joinRoomRequest.getPlayer().getId().toString(),
                    "/queue/errors",
                    new GameException("Không thể tham gia phòng: " + e.getMessage())
            );

            return null;
        }
    }

    @MessageMapping("/room.leave")
    public void handleLeaveRoom(String roomId, Player player) {
        log.info("Received leave room request: room={}, player={}",
                roomId, player.getId());
        try {
            roomService.leaveRoom(roomId, player);
        } catch (Exception e) {
            log.error("Error leaving room: ", e);
            simpMessagingTemplate.convertAndSendToUser(
                    player.getId().toString(),
                    "/queue/errors",
                    new GameException("Lỗi khi rời phòng: " + e.getMessage())
            );
        }
    }

    // API endpoint để lấy danh sách phòng
    @MessageMapping("/rooms")
    @SendTo("/topic/rooms")
    public List<ListRoomResponse> getRooms() {
        log.info("Received request for room list");
        List<ListRoomResponse> rooms = roomService.getRooms();
        log.info("Returning {} rooms", rooms.size());
        return rooms;
    }
}
