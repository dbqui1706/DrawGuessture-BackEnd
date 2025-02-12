package fit.nlu.model;

import fit.nlu.enums.RoomState;
import fit.nlu.service.GameEventNotifier;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class Room implements Serializable {
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
        this.state = RoomState.WAITING;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.setting = new RoomSetting();
        addPlayer(owner);
//        this.chatSystem = new ChatSystem(this);
    }

    // Thêm các phương thức hỗ trợ
    public boolean canJoin() {
        return players.size() < setting.getMaxPlayer();
    }

    public synchronized void addPlayer(Player player) {
        if (canJoin()) {
            player.setRoomId(id.toString());
            players.put(player.getId(), player);
        } else {
            throw new RuntimeException("Room is full");
        }
    }

    public synchronized void removePlayer(UUID playerId) {
        players.remove(playerId);
    }

    // Phương thức khởi động GameSession
    public void startGameSession(GameEventNotifier notifier) {
        int totalRounds = setting.getTotalRound();
        int turnTimeLimit = setting.getDrawingTime();
        List<Player> playerList = getCurrentPlayers();
        this.gameSession = new GameSession(playerList, totalRounds, turnTimeLimit, id.toString(), notifier);
        gameSession.startGame();
    }

    // Kết thúc GameSession
    public void endGameSession() {
        if (gameSession != null) {
            gameSession.endGame();
            gameSession = null;
        }
    }

    public synchronized List<Player> getCurrentPlayers() {
        return new ArrayList<>(players.values());
    }

    public synchronized void updatePlayerDrawing(UUID playerId) {
        players.forEach((id, player) -> {
            player.setDrawing(id.equals(playerId));
        });
    }

    public void updateScoreForPlayers(List<Player> currentPlayers) {
        for (Player roomPlayer : getCurrentPlayers()) {
            currentPlayers.stream()
                    .filter(turnPlayer -> turnPlayer.getId().equals(roomPlayer.getId()))
                    .findFirst()
                    .ifPresent(turnPlayer -> {
                        roomPlayer.setScore(turnPlayer.getScore());
                    });
        }
    }
}