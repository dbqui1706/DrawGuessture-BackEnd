package fit.nlu.model;

import fit.nlu.enums.MessageType;
import lombok.Data;

import java.util.UUID;

@Data
public class Message {
    private UUID id;
    private Player sender;
    private String content;
    private MessageType type;
    private MessageMetadata metadata;
    private long createdAt;

    public Message() {
        this.id = UUID.randomUUID();
        this.metadata = new MessageMetadata();
        this.createdAt = System.currentTimeMillis();
    }
}
