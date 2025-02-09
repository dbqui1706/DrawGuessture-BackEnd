package fit.nlu.dto.response;

import fit.nlu.model.Player;
import lombok.Data;

@Data
public class TurnDto {
    private String turnId;
    private Player drawer;
    private int timeLimit;
    private int remainingTime; // Thời gian còn lại của turn
    private String keyword; // Chỉ có giá trị với người vẽ; null đối với người đoán
    private String eventType; // Ví dụ: "TURN_START"

    public TurnDto() {
    }

    public TurnDto(String turnId, Player drawer, int timeLimit, String keyword, String eventType) {
        this.turnId = turnId;
        this.drawer = drawer;
        this.timeLimit = timeLimit;
        this.keyword = keyword;
        this.eventType = eventType;
    }
}
