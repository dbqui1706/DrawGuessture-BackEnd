package fit.nlu.model;

import java.sql.Timestamp;
import java.util.*;

public class Round {
    private UUID id;
    private List<Turn> turns;
    private Turn currentTurn;
    private Queue<Player> remainingPlayers;
    private Set<Player> completedPlayers;
    private Map<UUID, Integer> roundScores;
    private Timestamp startTime;
    private Timestamp endTime;
}
