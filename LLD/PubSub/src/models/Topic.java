package models;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;

@Getter
@AllArgsConstructor
public class Topic {
    private final String topicId;
    private final Set<String> subscriberIds;

    public void addSubscriber(String subscriberId){
        subscriberIds.add(subscriberId);
    }

    public void removeSubscriber(String subscriberId){
        subscriberIds.remove(subscriberId);
    }
}
