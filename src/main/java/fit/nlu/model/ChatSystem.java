package fit.nlu.model;

import lombok.*;

import java.util.List;

@Data
@Getter
@Setter
@NoArgsConstructor
public class ChatSystem {
    private Room room;
    private List<Message> messages;
    private int messageLimit;
    private List<Message> systemMessages;

    public ChatSystem(Room room) {
        this.room = room;
        this.messages = List.of();
        this.messageLimit = 300;
        this.systemMessages = List.of();
    }
}
