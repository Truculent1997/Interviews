package models.subscriber;

import models.message.Message;

public class ConsoleSubscriber extends Subscriber {
    private final String subscriberId;
    
    public ConsoleSubscriber(String subscriberId) {
        this.subscriberId = subscriberId;
    }
    
    @Override
    public void receiveMessage(Message message) {
        System.out.println("[" + subscriberId + "] Received: " + message.getMessage());
    }
    
    public String getSubscriberId() {
        return subscriberId;
    }
}
