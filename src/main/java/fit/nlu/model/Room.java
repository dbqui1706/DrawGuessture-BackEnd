package fit.nlu.model;

import fit.nlu.enums.RoomState;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class Room {
    private UUID id;
    private Player owner;
    private Map<UUID, Player> players;
    private ChatSystem chatSystem;
    private GameSession gameSession;
    private RoomSetting setting;
    private RoomState state;
    private Timestamp createdAt;

    public Room(Player owner) {
        this.id = UUID.randomUUID();
        this.owner = owner;
        this.players = new ConcurrentHashMap<>();
        this.players.put(owner.getId(), owner);
        this.setting = new RoomSetting();
        this.gameSession = new GameSession(this, List.of(owner), setting.getTotalRound());
        this.state = RoomState.WAITING;
        this.createdAt = new Timestamp(System.currentTimeMillis());
//        this.chatSystem = new ChatSystem(this);
    }

    // Thêm các phương thức hỗ trợ
    public boolean canJoin() {
        return players.size() < setting.getMaxPlayer();
    }

    public void addPlayer(Player player) {
        if (canJoin()) {
            player.setRoomId(id.toString());
            players.put(player.getId(), player);
        } else {
            throw new RuntimeException("Room is full");
        }
    }

    public void removePlayer(UUID playerId) {
        players.remove(playerId);
    }

}