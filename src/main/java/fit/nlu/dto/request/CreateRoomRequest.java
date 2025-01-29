package fit.nlu.dto.request;

import fit.nlu.enums.RoomState;
import fit.nlu.model.ChatSystem;
import fit.nlu.model.Player;
import fit.nlu.model.RoomSetting;
import lombok.Data;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
public class CreateRoomRequest {
    private UUID id;
    private Player owner;
    private Map<UUID, Player> players;
    private RoomSetting setting;
    private RoomState state;

    public CreateRoomRequest(Player owner) {
        id = UUID.randomUUID();
        this.owner = owner;
        this.players = new HashMap<>();
        players.put(owner.getId(), owner);
        setting = new RoomSetting();
        state = RoomState.WAITING;
    }
}
