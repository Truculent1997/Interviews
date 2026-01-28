package models.subscriber;

import models.message.Message;

public abstract class Subscriber {
    private String subscriberId;
    abstract public void receiveMessage(Message message);
}
