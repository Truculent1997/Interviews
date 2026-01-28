package models.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;

@Getter
@AllArgsConstructor
public abstract class Message<T> {
    private final String messageId;
    private final String topicId;
    private final String partitionKey;
    private T message;
}
