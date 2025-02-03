package fit.nlu.websocket;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Setter
@Getter
public class WebSocketMessage<T> {
    private String type;
    private T data;
    private String timestamp;

    // Thêm constants cho message types
    public static final String PLAYER_JOINED = "PLAYER_JOINED";
    public static final String PLAYER_LEFT = "PLAYER_LEFT";
    public static final String ROOM_LIST_UPDATED = "ROOM_LIST_UPDATED";
    public static final String ROOM_STATE_CHANGED = "ROOM_STATE_CHANGED";
    public static final String PLAYER_STATUS_CHANGED = "PLAYER_STATUS_CHANGED";

    public WebSocketMessage(String type, T data) {
        this.type = type;
        this.data = data;
        this.timestamp = LocalDateTime.now().toString();
    }
}
