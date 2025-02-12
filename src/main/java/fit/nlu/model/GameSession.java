package fit.nlu.model;

import fit.nlu.enums.GameState;
import fit.nlu.service.GameEventNotifier;
import fit.nlu.service.RoomEventNotifier;
import lombok.Data;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class GameSession {
    private final String id;
    private final List<Round> rounds;
    private Round currentRound;
    private List<Player> players;
    private GameState state;
    private Timestamp startTime;
    private Timestamp endTime;
    private int currentRoundNumber;
    private final int totalRounds;
    private final int turnTimeLimit;
    private final String roomId;
    private final GameEventNotifier notifier;

    public GameSession(List<Player> players, int totalRounds,
                       int turnTimeLimit, String roomId,
                       GameEventNotifier notifier ) {
        this.id = UUID.randomUUID().toString();
        this.players = players;
        this.totalRounds = totalRounds;
        this.turnTimeLimit = turnTimeLimit;
        this.rounds = new ArrayList<>();
        this.state = GameState.WAITING;
        this.roomId = roomId;
        this.notifier = notifier;
    }

    public void startGame() {
//        if (state == GameState.GAME_END) return;
        this.state = GameState.PLAYING;
        this.startTime = new Timestamp(System.currentTimeMillis());
        this.currentRoundNumber = 1;
        System.out.println("Game started.");
        notifier.notifyGameStart(roomId);
        startRound();
    }

    private void startRound() {
        currentRound = new Round(players, turnTimeLimit, roomId, notifier, currentRoundNumber);
        rounds.add(currentRound);
        currentRound.startRound(() -> {
            if (currentRoundNumber < totalRounds) {
                currentRoundNumber++;
                startRound();
            } else {
                endGame();
            }
        });
    }

    public void endGame() {
        this.state = GameState.GAME_END;
        this.endTime = new Timestamp(System.currentTimeMillis());
        System.out.println("Game ended.");

        // Dừng turn đang chạy nếu có
        if (currentRound != null && currentRound.getCurrentTurn() != null) {
            currentRound.getCurrentTurn().cancelTurn();
        }

        // Clear game session
        clearGameSession();
        notifier.notifyGameEnd(roomId);
    }


    public void updatePlayers(List<Player> newPlayers) {
        this.players = newPlayers;
    }

    public void clearGameSession() {
        rounds.clear();
        currentRound = null;
        players.clear();
        state = GameState.WAITING;
        startTime = null;
        endTime = null;
        currentRoundNumber = 0;
    }

    public Turn getCurrentTurn() {
        if (state != GameState.PLAYING) return null;
        if (currentRound == null) return null;
        return getCurrentRound().getCurrentTurn();
    }
}
