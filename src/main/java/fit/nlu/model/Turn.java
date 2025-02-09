package fit.nlu.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fit.nlu.enums.TurnState;
import fit.nlu.service.GameEventNotifier;
import lombok.Getter;
import org.slf4j.Logger;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.*;

@Getter
public class Turn implements Serializable {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(Turn.class);
    private final String id;
    @Getter
    private final Player drawer;
    private final String keyword;
    private final List<Guess> guesses;
    private TurnState state;
    private Timestamp startTime;
    private Timestamp endTime;
    private final int timeLimit; // giây
    private Stack<DrawingData> drawingDataList;
    private Stack<DrawingData> redoDrawingDataStack;
    @Getter
    private int serverRemainingTime; // Thời gian còn lại được tính từ server
    @JsonIgnore
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    @JsonIgnore
    private final ScheduledExecutorService timeUpdateScheduler = Executors.newSingleThreadScheduledExecutor();
    @JsonIgnore
    private ScheduledFuture<?> scheduledTask;
    @JsonIgnore
    private ScheduledFuture<?> timeUpdateTask;
    @JsonIgnore
    private final String roomId;
    @JsonIgnore
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
        this.drawingDataList = new Stack<>();
        this.redoDrawingDataStack = new Stack<>();
    }

    public void startTurn(Runnable onTurnEndCallback) {
        if (state == TurnState.COMPLETED) return;
        this.state = TurnState.IN_PROGRESS;
        this.startTime = new Timestamp(System.currentTimeMillis());
        System.out.println("Turn started for drawer: " + drawer.getNickname());
        notifier.notifyTurnStart(roomId, this);

        // Gửi cập nhật thời gian mỗi giây
        timeUpdateTask = timeUpdateScheduler.scheduleAtFixedRate(() -> {
            serverRemainingTime = getRemainingTime();
            if (serverRemainingTime > 0) {
                notifier.notifyTurnTimeUpdate(roomId, this);
            }
        }, 0, 1, TimeUnit.SECONDS);

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
        // Hủy các task đang chạy
        if (timeUpdateTask != null) {
            timeUpdateTask.cancel(true);
        }
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
        }

        timeUpdateScheduler.shutdown();
        scheduler.shutdown();
    }

    public synchronized void submitGuess(Guess guess) {
        if (state != TurnState.IN_PROGRESS) return;
        if (guess.getContent().equalsIgnoreCase(keyword)) {
            guesses.add(guess);
        }
    }

    public int getRemainingTime() {
        if (startTime == null) return timeLimit;
        long elapsedMillis = System.currentTimeMillis() - startTime.getTime();
        int elapsedSeconds = (int) (elapsedMillis / 1000);
        int remaining = timeLimit - elapsedSeconds;
        return Math.max(0, remaining);
    }

    public void cancelTurn() {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(true); // Hủy task đang chờ
        }
        if (timeUpdateTask != null && !timeUpdateTask.isDone()) {
            timeUpdateTask.cancel(true); // Hủy task cập nhật thời gian
        }
        scheduler.shutdownNow(); // Dừng scheduler ngay lập tức
        timeUpdateScheduler.shutdownNow(); // Dừng scheduler ngay lập tức
        this.state = TurnState.COMPLETED;
        System.out.println("Turn forcibly stopped for drawer: " + drawer.getNickname());
    }

    public void addDrawingData(DrawingData drawingData) {
        DrawingData.ActionType actionType = drawingData.getActionType();
        switch (actionType) {
            case START, MOVE, UP:
                drawingDataList.add(drawingData);
                break;
            case CLEAR:
                drawingDataList.clear();
                break;
            case UNDO:
                if (!drawingDataList.isEmpty()) {
                    redoDrawingDataStack.push(drawingDataList.pop());
                }
                break;
            case REDO:
                if (!redoDrawingDataStack.isEmpty()) {
                    drawingDataList.push(redoDrawingDataStack.pop());
                }
                break;
        }
    }

}
