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
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class Turn implements Serializable {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(Turn.class);
    private final String id;
    private final Player drawer;
    private final String keyword;
    private TurnState state;
    private Timestamp startTime;
    private Timestamp startTimeShowResult;
    private Timestamp endTime;
    private final int timeLimit; // giây
    private final String roomId;
    private final Set<Guess> guesses;
    private final Stack<DrawingData> drawingDataList;
    private final Stack<DrawingData> redoDrawingDataStack;
    private final List<Player> currentPlayers;
    private int serverRemainingTime; // Thời gian còn lại được tính từ server
    private int serverRemainingTimeShowResult; // Thời gian còn lại được tính từ server
    private int resultDisplayCountdown = 10; // Đếm ngược trước khi kết thúc turn
    @JsonIgnore
    private final ScheduledExecutorService scheduler;
    @JsonIgnore
    private final ScheduledExecutorService timeUpdateScheduler;
    @JsonIgnore
    private final ScheduledExecutorService resultScheduler;
    @JsonIgnore
    private final ScheduledExecutorService timeResultScheduler;
    @JsonIgnore
    private ScheduledFuture<?> scheduledTask;
    @JsonIgnore
    private ScheduledFuture<?> timeUpdateTask;
    @JsonIgnore
    private ScheduledFuture<?> resultTask;
    @JsonIgnore
    private ScheduledFuture<?> timeResultTask;
    @JsonIgnore
    private final GameEventNotifier notifier;
    @JsonIgnore
    private Runnable onTurnEndCallback;

    public Turn(Player drawer, String keyword, int timeLimit, String roomId, GameEventNotifier notifier, List<Player> players) {
        this.id = UUID.randomUUID().toString();
        this.drawer = drawer;
        this.keyword = keyword;
        this.guesses = new ConcurrentSkipListSet<>(Comparator.comparing(Guess::getTimeTaken));
        this.state = TurnState.NOT_STARTED;
        this.timeLimit = timeLimit;
        this.roomId = roomId;
        this.notifier = notifier;
        this.currentPlayers = players;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.timeUpdateScheduler = Executors.newSingleThreadScheduledExecutor();
        this.resultScheduler = Executors.newSingleThreadScheduledExecutor();
        this.timeResultScheduler = Executors.newSingleThreadScheduledExecutor();
        this.drawingDataList = new Stack<>();
        this.redoDrawingDataStack = new Stack<>();
    }

    public void startTurn(Runnable onTurnEndCallback) {
        if (state == TurnState.COMPLETED) return;
        this.onTurnEndCallback = onTurnEndCallback;
        this.state = TurnState.IN_PROGRESS;
        this.startTime = new Timestamp(System.currentTimeMillis());

        System.out.println("Turn started for drawer: " + drawer.getNickname());
        notifier.notifyTurnStart(roomId, this);

        // Gửi cập nhật thời gian mỗi giây
        timeUpdateTask = timeUpdateScheduler.scheduleAtFixedRate(() -> {
            if ((serverRemainingTime = getRemainingTime()) > 0) {
                notifier.notifyTurnTimeUpdate(roomId, this);
            }
        }, 0, 1, TimeUnit.SECONDS);

        // Lên lịch tự động kết thúc turn sau timeLimit giây
        scheduledTask = scheduler.schedule(() -> {
            completedTurn(() -> startThreadShowResultTurn(this.onTurnEndCallback));
        }, timeLimit, TimeUnit.SECONDS);
    }


    public synchronized void completedTurn(Runnable onShowResultCallback) {
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

        // Tính điểm
        calculateScore();

        // Gửi thông báo kết thúc turn
        notifier.notifyTurnEnd(roomId, this);

        // Gọi callback để xử lý hiển thị kết quả
        if (onShowResultCallback != null) {
            onShowResultCallback.run();
        }
    }


    public void startThreadShowResultTurn(Runnable onShowResultEndCallback) {
        startTimeShowResult = new Timestamp(System.currentTimeMillis());

        resultTask = timeResultScheduler.scheduleAtFixedRate(() -> {
            if ((serverRemainingTimeShowResult = getRemainingTimeShowResult()) > 0) {
                log.info("Turn result countdown: {}", serverRemainingTimeShowResult);
                notifier.notifyTurnResultCountdown(roomId, this);
            } else {
                log.info("Turn result countdown: 0");
                cleanupResultSchedulers();
            }
        }, 0, 1, TimeUnit.SECONDS);

        // Lên lịch delay 10 giây để kết thúc hiển thị kết quả
        timeResultTask = resultScheduler.schedule(() -> {
            log.info("Turn result displayed, moving to next turn");
            cleanupResultSchedulers();

            // Gọi callback nếu có
            if (onShowResultEndCallback != null) {
                onShowResultEndCallback.run();
            }
        }, resultDisplayCountdown, TimeUnit.SECONDS);
    }

    private void cleanupResultSchedulers() {
        if (resultTask != null) resultTask.cancel(true);
        if (timeResultTask != null) timeResultTask.cancel(true);
        resultScheduler.shutdown();
        timeResultScheduler.shutdown();
    }


    public synchronized void submitGuess(Guess guess) {
        if (state != TurnState.IN_PROGRESS) return;
        // Nếu người chơi đã đoán trước đó, không thêm lại.
        if (guesses.stream().anyMatch(g -> g.getPlayer().getId().equals(guess.getPlayer().getId()))) return;
        guess.setTimestamp(startTime);
        guesses.add(guess);

        // chỉ gọi hàm kiểm tra riêng để lên lịch kết thúc turn sau delay nếu đủ điều kiện.
        checkAndScheduleEndTurn();
    }

    /**
     * Tính điểm cho các guess đúng và cập nhật vào điểm của người chơi.
     * Giả sử: điểm = (timeLimit - secondsTaken) * 10.
     * Chỉ những guess có nội dung chính xác (exact match) với từ khóa mới được tính điểm.
     */
    public void calculateScore() {
        // Không cần tính toán nếu không có ai đoán đúng
        if (guesses.isEmpty()) return;

        // Tính điểm cho những người đoán đúng
        for (Guess guess : guesses) {
            // Tính số giây đã dùng để đoán
            int secondsTaken = (int) ((guess.getTimeTaken().getTime() - startTime.getTime()) / 1000);
            // Điểm = (thời gian còn lại) * 5
            int score = Math.max(0, (timeLimit - secondsTaken)) * 5;
            guess.setScore(score);

            // Cập nhật điểm cho người đoán
            for (Player player : currentPlayers) {
                if (player.getId().equals(guess.getPlayer().getId())) {
                    player.addScore(score);
                    log.info("Player {} scored {} points", player.getNickname(), score);
                    break;
                }
            }
        }
        // Tính điểm thưởng cho người vẽ (20 điểm/người đoán đúng)
        int drawerBonus = guesses.size() * 20;
        for (Player player : currentPlayers) {
            if (player.getId().equals(drawer.getId())) {
                player.addScore(drawerBonus);
                log.info("Drawer {} got bonus {} points", player.getNickname(), drawerBonus);
                break;
            }
        }
    }

    /**
     * Kiểm tra nếu đủ người đoán đúng và lên lịch kết thúc turn sau một khoảng delay ngắn.
     */
    private synchronized void checkAndScheduleEndTurn() {
        // Kiểm tra: số lượng guess đạt đủ số người chơi hiện tại (trừ người vẽ)
        if (currentPlayers != null && guesses.size() >= currentPlayers.size() - 1) {
            // Lên lịch kết thúc turn sau 500ms để đảm bảo tin nhắn đoán cuối cùng được xử lý.
            scheduler.schedule(() -> {
                // Kiểm tra lại điều kiện và trạng thái trước khi kết thúc
                if (state != TurnState.COMPLETED && guesses.size() >= currentPlayers.size() - 1) {
                    completedTurn(() -> startThreadShowResultTurn(this.onTurnEndCallback));
                }
            }, 500, TimeUnit.MILLISECONDS);
        }
    }

    public int getRemainingTime() {
        if (startTime == null) return timeLimit;
        long elapsedMillis = System.currentTimeMillis() - startTime.getTime();
        int elapsedSeconds = (int) (elapsedMillis / 1000);
        int remaining = timeLimit - elapsedSeconds;
        return Math.max(0, remaining);
    }

    public int getRemainingTimeShowResult() {
        if (startTimeShowResult == null) return resultDisplayCountdown;
        long elapsedMillis = System.currentTimeMillis() - startTimeShowResult.getTime();
        int elapsedSeconds = (int) (elapsedMillis / 1000);
        int remaining = resultDisplayCountdown - elapsedSeconds;
        return Math.max(0, remaining);
    }

    public synchronized void cancelTurn() {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(true); // Hủy task đang chờ
        }
        if (timeUpdateTask != null && !timeUpdateTask.isDone()) {
            timeUpdateTask.cancel(true); // Hủy task cập nhật thời gian
        }
        if (resultTask != null && !resultTask.isDone()) {
            resultTask.cancel(true); // Hủy task cập nhật thời gian
        }
        if (timeResultTask != null && !timeResultTask.isDone()) {
            timeResultTask.cancel(true); // Hủy task cập nhật thời gian
        }
        // Dừng các scheduler ngay lập tức
        scheduler.shutdownNow();
        timeUpdateScheduler.shutdownNow();
        resultScheduler.shutdownNow();
        timeResultScheduler.shutdownNow();
        this.state = TurnState.COMPLETED;
        System.out.println("Turn forcibly stopped for drawer: " + drawer.getNickname());
    }

    public synchronized void addPlayer(Player player) {
        currentPlayers.add(player);
    }

    public synchronized void removePlayer(Player player) {
        if (currentPlayers != null) {
            currentPlayers.removeIf(p -> p.getId().equals(player.getId()));
        }
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
