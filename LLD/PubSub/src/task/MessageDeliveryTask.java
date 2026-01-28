package task;

import models.message.Message;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class MessageDeliveryTask {
    Message message;
    String subscriberId;
    int numberOfRetries;
}
