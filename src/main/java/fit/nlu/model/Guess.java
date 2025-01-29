package fit.nlu.model;

import java.sql.Timestamp;
import java.util.UUID;

public class Guess {
    private UUID id;
    private Player player;
    private Turn turn;
    private String content;
    private int point;
    private Timestamp timestamp;
    private Timestamp timeTaken;
}
