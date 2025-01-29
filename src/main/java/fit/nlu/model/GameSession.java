package fit.nlu.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fit.nlu.enums.GameState;
import lombok.Data;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // Ignore this class when serializing to JSON
public class GameSession {
    private UUID id;
    @JsonIgnore
    private Room room;
    private List<Round> rounds;
    private Round currentRound;
    private List<Player> players;
    private Map<UUID, Integer> scores;
    private GameState state;
    private Timestamp startTime;
    private Timestamp endTime;

    public GameSession(Room room, List<Player> players, int totalRound) {
        this.id = UUID.randomUUID();
        this.room = room;
        this.players = players;
        this.scores = new ConcurrentHashMap<>();
        for (Player player : players) {
            scores.put(player.getId(), 0);
        }
        this.rounds = new ArrayList<>(totalRound);
        this.state = GameState.WAITING;
        this.startTime = new Timestamp(System.currentTimeMillis());
    }
}
