package fit.nlu.model;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

public class Turn {
    private UUID id;
    private Player drawer;
    private String keyword;
    private List<Guess> guesses;
    private Drawing drawing;
    private Timestamp startTime;
    private Timestamp endTime;
}
