package fit.nlu.model;

import fit.nlu.enums.RoundState;
import fit.nlu.service.GameEventNotifier;
import fit.nlu.service.RoomEventNotifier;
import lombok.Data;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.*;

@Data
public class Round implements Serializable {
    private final String id;
    private final List<Turn> turns;
    private Turn currentTurn;
    private final List<Player> players;
    private final Queue<Player> remainingPlayers;
    private final Set<Player> completedPlayers;
    private RoundState state;
    private Timestamp startTime;
    private Timestamp endTime;
    private final int turnTimeLimit;
    private final String roomId;
    private final GameEventNotifier notifier;
    private final int roundNumber;

    public Round(List<Player> players, int turnTimeLimit,
                 String roomId, GameEventNotifier notifier,
                 int roundNumber) {
        this.id = UUID.randomUUID().toString();
        this.turns = new ArrayList<>();
        this.players = new ArrayList<>(players);
        this.remainingPlayers = new LinkedList<>(players);
        this.completedPlayers = new HashSet<>();
        this.state = RoundState.NOT_STARTED;
        this.turnTimeLimit = turnTimeLimit;
        this.roomId = roomId;
        this.notifier = notifier;
        this.roundNumber = roundNumber;
    }

    public void startRound(Runnable onRoundEndCallback) {
        if (state == RoundState.COMPLETED) return;
        this.state = RoundState.PLAYING;
        this.startTime = new Timestamp(System.currentTimeMillis());
        System.out.println("Round started: " + roundNumber);
        notifier.notifyRoundStart(roomId, roundNumber);

        nextTurn(onRoundEndCallback);
    }

    public synchronized void nextTurn(Runnable onRoundEndCallback) {
        if (!remainingPlayers.isEmpty()) {
            Player nextDrawer = remainingPlayers.poll();
            String keyword = KeywordGenerator.getRandomKeyword();
            // Tạo instance mới cho turn tiếp theo:
            Turn newTurn = new Turn(nextDrawer, keyword, turnTimeLimit, roomId, notifier, players);
            // (Nếu cần, cập nhật danh sách currentPlayers cho newTurn)
            this.currentTurn = newTurn;
            this.turns.add(newTurn);
            newTurn.startTurn(() -> {
                completedPlayers.add(nextDrawer);
                nextTurn(onRoundEndCallback);
            });
        } else {
            endRound();
            notifier.notifyRoundEnd(roomId, roundNumber);
            onRoundEndCallback.run();
        }
    }

    public synchronized void endRound() {
        if (state == RoundState.COMPLETED) return;
        this.state = RoundState.COMPLETED;
        this.endTime = new Timestamp(System.currentTimeMillis());
        // clear round data
        clearRoundData();
        System.out.println("Round completed: " + roundNumber);
    }

    private void clearRoundData() {
        state = RoundState.NOT_STARTED;
        remainingPlayers.clear();
        completedPlayers.clear();
        currentTurn = null;
        turns.clear();
    }

    /**
     * Thêm người chơi vào remainingPlayers nếu họ chưa có.
     */
    public synchronized void addPlayer(Player player) {
        // Kiểm tra nếu người chơi chưa được hoàn thành turn và chưa tồn tại trong remainingPlayers
        if (!remainingPlayers.contains(player) && !completedPlayers.contains(player)) {
            remainingPlayers.add(player);
        }
    }

    /**
     * Loại bỏ người chơi khỏi remainingPlayers nếu tồn tại.
     */
    public synchronized void removePlayer(Player player) {
        if (remainingPlayers.remove(player)) {
        }
    }

    public Turn getCurrentTurn() {
        return currentTurn;
    }

    public synchronized Queue<Player> getRemainingPlayers() {
        return new LinkedList<>(remainingPlayers);
    }
}
