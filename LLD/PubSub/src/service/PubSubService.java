package service;

import com.google.common.hash.Hashing;
import models.Topic;
import models.message.Message;
import models.subscriber.Subscriber;
import task.MessageDeliveryTask;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class PubSubService {
    private final Map<String, Subscriber> subscriberIdtoSubscriberMap;
    private final Map<String, Topic> topicRegistry;
    private final Map<String, BlockingDeque<MessageDeliveryTask>> dlqueues;
    private final Map<Integer, BlockingDeque<MessageDeliveryTask>> queues;
    private final Map<Integer, ExecutorService> perQueueExecutorService;
    private final List<ExecutorService> fanOutWorkers;
    private final int NUM_WORKERS = 10;


    int noOfQueues = 10;

    public PubSubService() {
        fanOutWorkers = new ArrayList<>();
        for (int i = 0; i < NUM_WORKERS; i++) {
            fanOutWorkers.add(Executors.newSingleThreadExecutor());
        }
        subscriberIdtoSubscriberMap = new ConcurrentHashMap<>();
        topicRegistry = new ConcurrentHashMap<>();
        perQueueExecutorService = new ConcurrentHashMap<>();
        dlqueues = new ConcurrentHashMap<>();
        queues = new ConcurrentHashMap<>();
        for (int i = 0; i <= 9; i++) {
            queues.put(i, new LinkedBlockingDeque<>());
            perQueueExecutorService.put(i, Executors.newSingleThreadExecutor());

            final int queueId = i;
            perQueueExecutorService.get(i).submit(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        MessageDeliveryTask messageDeliveryTask = queues.get(queueId).take();
                        try {

                            processMessage(messageDeliveryTask);
                        } catch (Exception ex) {
                            if (messageDeliveryTask.getNumberOfRetries() < 3) {
                                messageDeliveryTask.setNumberOfRetries(messageDeliveryTask.getNumberOfRetries() + 1);
                                queues.get(queueId).add(messageDeliveryTask);
                            } else {
                                dlqueues.computeIfAbsent(messageDeliveryTask.getSubscriberId(),
                                                k -> new LinkedBlockingDeque<>())
                                        .add(messageDeliveryTask);
                            }
                        }

                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }

    public void processMessage(MessageDeliveryTask task) {
        Subscriber subscriber = subscriberIdtoSubscriberMap.get(task.getSubscriberId());
        if (subscriber != null) {
            subscriber.receiveMessage(task.getMessage());
        }
    }

    public void publishMessage(Message<String> message) {
        Topic topic = topicRegistry.get(message.getTopicId());
        if (topic == null) {
            throw new RuntimeException("Topic doesn't exist");
        }
        
        int workerIndex = Math.abs(message.getTopicId().hashCode()) % NUM_WORKERS;

        fanOutWorkers.get(workerIndex).submit(() -> {
            topic.getSubscriberIds().parallelStream().forEach(subscriberId -> {
                String key = message.getPartitionKey() + ":" + subscriberId;
                int queueIndex = Hashing.consistentHash(Hashing.murmur3_128().hashString(key, StandardCharsets.UTF_8), noOfQueues);
                MessageDeliveryTask messageDeliveryTask = new MessageDeliveryTask(message, subscriberId, 0);
                queues.get(queueIndex).add(messageDeliveryTask);
            });
        });

    }

    public void addSubscriber(String topicId, String subscriberId) {
        if (!topicRegistry.containsKey(topicId)) {
            throw new RuntimeException("Topic doesn't exist");
        }

        Topic topic = topicRegistry.get(topicId);
        topic.addSubscriber(subscriberId);
    }

    public void createTopic(String topicId) {
        if (topicRegistry.containsKey(topicId)) {
            throw new RuntimeException("Topic exists");
        }

        Topic topic = new Topic(topicId, ConcurrentHashMap.newKeySet());
        topicRegistry.put(topicId, topic);
    }

    public void registerSubscriber(String subscriberId) {
        if (subscriberIdtoSubscriberMap.containsKey(subscriberId)) {
            throw new RuntimeException("Subscriber already registered");
        }
        Subscriber subscriber = new Subscriber() {
            @Override
            public void receiveMessage(Message message) {
                System.out.println(message.getMessage());
            }
        };
        subscriberIdtoSubscriberMap.put(subscriberId, subscriber);
    }

    public void removeSubscriber(String subscriberId, String topicId) {
        Topic topic = topicRegistry.get(topicId);
        topic.removeSubscriber(subscriberId);
    }

    public void unregisterSubscriber(String subscriberId) {
        if (subscriberIdtoSubscriberMap.containsKey(subscriberId)) {
            subscriberIdtoSubscriberMap.remove(subscriberId);
        }
    }

    public void shutdown() {
        fanOutWorkers.forEach(ExecutorService::shutdown);
        perQueueExecutorService.values().forEach(ExecutorService::shutdown);
        
        try {
            for (ExecutorService worker : fanOutWorkers) {
                worker.awaitTermination(10, TimeUnit.SECONDS);
            }
            for (ExecutorService executor : perQueueExecutorService.values()) {
                executor.awaitTermination(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            fanOutWorkers.forEach(ExecutorService::shutdownNow);
            perQueueExecutorService.values().forEach(ExecutorService::shutdownNow);
            Thread.currentThread().interrupt();
        }
    }
}
