package fit.nlu.controller;

import fit.nlu.dto.request.CreateRoomRequest;
import fit.nlu.dto.response.ListRoomResponse;
import fit.nlu.model.Player;
import fit.nlu.model.Room;
import fit.nlu.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/game")
public class GameController {
    private static final Logger log = LoggerFactory.getLogger(GameController.class);
    @Autowired
    private RoomService roomService;

    public GameController(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<ListRoomResponse>> getRooms() {
        return ResponseEntity.ok(roomService.getRooms());
    }

    @PostMapping("/create-room")
    public ResponseEntity<Room> createRoom(@RequestBody Player owner) {
        log.info("Received create room request: {}", owner);
        try {
            Room room = roomService.createRoom(owner);
            return ResponseEntity.ok(room);
        } catch (Exception e) {
            log.error("Error creating room: ", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/join-room")
    public ResponseEntity<Room> joinRoom(@RequestParam String roomId, @RequestBody Player player) {
        log.info("Received join room request: {}", player);
        try {
            Room room = roomService.joinRoom(roomId, player);
            return ResponseEntity.ok(room);
        } catch (Exception e) {
            log.error("Error joining room: ", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
