package fit.nlu.controller;

import fit.nlu.dto.response.ListRoomResponse;
import fit.nlu.model.Player;
import fit.nlu.model.Room;
import fit.nlu.service.RoomService;
import io.lettuce.core.dynamic.annotation.Param;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/game")
@CrossOrigin("*")
@RequiredArgsConstructor
public class GameREST {
    private final Logger log = LoggerFactory.getLogger(GameREST.class);
    private RoomService roomService;

    @Autowired
    public GameREST(RoomService roomService) {
        this.roomService = roomService;
    }

    @GetMapping("/rooms")
    public ResponseEntity<List<ListRoomResponse>> getRooms() {
        return ResponseEntity.ok(roomService.getRooms());
    }

    @PostMapping("/create-room")
    public ResponseEntity<Room> createRoom(@RequestBody Player owner) {
        log.info("Received create room request from player: {}", owner.getId());
        return ResponseEntity.ok(roomService.createRoom(owner));
    }

    @PostMapping("/join-room")
    public ResponseEntity<Room> joinRoom(@Param("roomId") String roomId, @RequestBody Player player) {
        log.info("Received join room request: room={}, player={}", roomId, player.getId());
        return ResponseEntity.ok(roomService.joinRoom(roomId, player));
    }

    @PostMapping("/leave-room")
    public void leaveRoom(@Param("roomId") String roomId, @RequestBody Player player) {
        log.info("Received leave room request: room={}, player={}", roomId, player.getId());
        roomService.leaveRoom(roomId, player);
    }

    @DeleteMapping("/clear-rooms")
    public boolean clearRooms() {
        log.info("Received clear rooms request");
        return roomService.clearRooms();
    }
}
