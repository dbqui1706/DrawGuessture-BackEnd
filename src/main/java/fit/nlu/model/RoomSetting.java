package fit.nlu.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;
@Getter
@Setter
public class RoomSetting {
    public static final int MIN_PLAYER_TO_START = 2;
    private int maxPlayer;
    private int totalRound;
    private int drawingTime;
    private Set<String> dictionary;
    private List<String> customWords;

    public RoomSetting() {
        this.maxPlayer = 8;
        this.totalRound = 3;
        this.drawingTime = 120;
        this.dictionary = Set.of("animal", "fruits", "countries");
        this.customWords = List.of();
    }
}
