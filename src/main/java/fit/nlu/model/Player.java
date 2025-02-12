package fit.nlu.model;

import fit.nlu.enums.PlayerStatus;
import lombok.Data;

import java.util.UUID;

@Data
public class Player {
    private UUID id;
    private String roomId;
    private String nickname;
    private boolean isOwner;
    private String avatar;
    private int score;
    private boolean drawing;
    private PlayerStatus status;

    public void addScore(int score) {
        this.score += score;
    }
}
