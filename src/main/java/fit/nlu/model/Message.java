package fit.nlu.model;

import fit.nlu.enums.MessageType;

import java.util.UUID;

public class Message {
    private UUID id;
    private Player sender;
    private String content;
    private MessageType type;
    private MessageMetadata metadata;
    private long createdAt;
}
