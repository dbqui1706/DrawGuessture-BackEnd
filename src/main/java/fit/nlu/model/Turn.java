package fit.nlu.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fit.nlu.enums.TurnState;
import fit.nlu.service.GameEventNotifier;
import lombok.Data;
import lombok.Getter;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Getter
public class Turn implements Serializable {
    private final String id;
    private final Player drawer;
    private final String keyword;
    private final List<Guess> guesses;
    private TurnState state;
    private Timestamp startTime;
    private Timestamp endTime;
    private final int timeLimit; // giây
    @JsonIgnore
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    @JsonIgnore
    private ScheduledFuture<?> scheduledTask;
    private final String roomId;
    private final GameEventNotifier notifier;

    public Turn(Player drawer, String keyword, int timeLimit, String roomId, GameEventNotifier notifier) {
        this.id = UUID.randomUUID().toString();
        this.drawer = drawer;
        this.keyword = keyword;
        this.guesses = new ArrayList<>();
        this.state = TurnState.NOT_STARTED;
        this.timeLimit = timeLimit;
        this.roomId = roomId;
        this.notifier = notifier;
    }

    public void startTurn(Runnable onTurnEndCallback) {
        this.state = TurnState.IN_PROGRESS;
        this.startTime = new Timestamp(System.currentTimeMillis());
        System.out.println("Turn started for drawer: " + drawer.getNickname());
        notifier.notifyTurnStart(roomId, this);

        // Lên lịch tự động kết thúc turn sau timeLimit giây
        scheduledTask = scheduler.schedule(() -> {
            completedTurn();
            notifier.notifyTurnEnd(roomId, this);
            onTurnEndCallback.run();
        }, timeLimit, TimeUnit.SECONDS);
    }

    public void completedTurn() {
        if (state == TurnState.COMPLETED) return;
        this.state = TurnState.COMPLETED;
        this.endTime = new Timestamp(System.currentTimeMillis());
        System.out.println("Turn completed for drawer: " + drawer.getNickname());
        scheduler.shutdown();
    }

    public synchronized void submitGuess(Guess guess) {
        if (state != TurnState.IN_PROGRESS) return;
        if (guess.getContent().equalsIgnoreCase(keyword)) {
            guesses.add(guess);
//            System.out.println("Đoán đúng từ: " + guess.getContent());
//
//            if (scheduledTask != null && !scheduledTask.isDone()) {
//                scheduledTask.cancel(false);
//            }
//            notifier.notifyTurnEnd(roomId, this);
        }
    }

    public Player getDrawer() {
        return drawer;
    }
}
