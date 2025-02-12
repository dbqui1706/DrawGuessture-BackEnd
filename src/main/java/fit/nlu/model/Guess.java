package fit.nlu.model;

import lombok.Data;

import java.sql.Timestamp;
import java.util.UUID;

@Data
public class Guess {
    private UUID id;
    private Player player;
    private int score;
    private String content;
    private Timestamp timestamp;
    private Timestamp timeTaken;

    public Guess(Player player, String guess) {
        this.id = UUID.randomUUID();
        this.player = player;
        this.content = guess;
        this.timeTaken = new Timestamp(System.currentTimeMillis());
    }
}
