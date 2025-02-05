package fit.nlu.model;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;
import java.util.Set;
@Getter
@Setter
@ToString
public class RoomSetting  implements Serializable {
    public static final int MIN_PLAYER_TO_START = 2;
    private int maxPlayer;
    private int totalRound;
    private int drawingTime;
    private int hintCount;
    private Set<String> dictionary;
    private List<String> customWords;

    public RoomSetting() {
        this.maxPlayer = 2;
        this.totalRound = 3;
        this.drawingTime = 60;
        this.hintCount = 1;
        this.dictionary = Set.of("animal", "fruits", "countries");
        this.customWords = List.of();
    }
}
