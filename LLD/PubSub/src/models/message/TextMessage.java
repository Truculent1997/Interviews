package models.message;

public class TextMessage extends Message<String> {
    public TextMessage(String messageId, String topicId, String partitionKey, String message) {
        super(messageId, topicId, partitionKey, message);
    }
}
