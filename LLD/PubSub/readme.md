# In-Memory Publisher-Subscriber (Pub-Sub) System

## 1. Problem Statement
Design a low-level, in-memory library for a Pub-Sub framework with the following strict requirements:
* **Push-Based Delivery:** Subscribers receive messages immediately via a callback interface.
* **Partition Support:** Topics are partitioned to allow parallel processing and scalability.
* **Strict Ordering:** Messages must be delivered in strict FIFO order per partition/entity.
* **High Throughput:** The system must handle millions of subscribers without blocking the publisher (Low Latency).
* **Reliability:** Must handle subscriber failures with an Async Retry mechanism (Backoff) and a Dead Letter Queue (DLQ).

---

## 2. Architecture Overview

To achieve high throughput and strict ordering, the system uses a **Two-Layer Asynchronous Architecture**.

### Layer 1: Async Fan-Out (Topic Processors)
* **Goal:** Decouple the **Publisher** from the **Subscriber Loop**.
* **Mechanism:** When a user publishes a message, it is hashed to a specific `TopicWorker` thread and the method returns immediately (O(1) Latency).
* **Why:** This ensures that a topic with 1 million subscribers does not block the API caller for seconds/minutes.

### Layer 2: Partitioned Delivery (Queue Workers)
* **Goal:** Deliver messages to subscribers in parallel while maintaining order.
* **Mechanism:** The `TopicWorker` iterates through subscribers and routes the message to a `PartitionQueue` using `Hash(SubscriberID + PartitionKey)`.
* **Why:** This provides **Subscriber Isolation**. If "Subscriber A" is slow or failing, it only backs up their assigned partition. "Subscriber B" (on a different partition) continues to process messages at full speed.

---

## 3. Key Design Decisions

| Feature | Decision | Reason |
| :--- | :--- | :--- |
| **Publisher Latency** | **Async Executor (Fire & Forget)** | Publisher cannot wait for 1M subscribers to be processed. API must be O(1). |
| **Ordering Strategy** | **Topic-Based Hashing** | All messages for "Topic A" go to the same background thread to ensure sequential processing order. |
| **Subscriber Routing** | **Consistent Hashing** | `Hash(SubID + Key)` ensures specific entities (e.g., updates for Order #123) always land in the same queue. |
| **Concurrency** | **Single-Threaded Executors** | Avoids locks and complex synchronization. One thread per queue guarantees strict FIFO execution. |
| **Reliability** | **Retry w/ Delay + DLQ** | Retries handle transient failures (network blips); DLQ prevents data loss after max retries. |

---

## 4. Final Implementation (Golden Copy)

Below is the consolidated, bug-free implementation including all helper classes.

### 4.1. The Message Model (`Message.java`)
```java
package models.message;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public abstract class Message<T> {
    private final String messageId;
    private final String topicId;
    private final String partitionKey;
    private T message;
}
```

### 4.2. The Task Wrapper (`MessageDeliveryTask.java`)
Wraps the message with delivery metadata (recipient & retry tracking).
```java
package task;

import models.message.Message;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter @AllArgsConstructor
public class MessageDeliveryTask {
    private Message message;
    private String subscriberId;
    private int retryCount;
}
```

### 4.3. The Core Service (`PubSubService.java`)
Includes fixes for the "Infinite Retry Loop" and "Double Parallelism" bugs found in earlier versions.

```java
package service;

import com.google.common.hash.Hashing;
import models.Topic;
import models.message.Message;
import models.subscriber.Subscriber;
import task.MessageDeliveryTask;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;

public class PubSubService {
    // 1. Storage & Registry
    private final Map<String, Topic> topicRegistry = new ConcurrentHashMap<>();
    private final Map<String, Subscriber> subscriberMap = new ConcurrentHashMap<>();
    
    // 2. Queues
    private final Map<Integer, BlockingQueue<MessageDeliveryTask>> partitionQueues = new ConcurrentHashMap<>();
    private final Map<String, BlockingQueue<MessageDeliveryTask>> dlq = new ConcurrentHashMap<>();

    // 3. Thread Pools
    private final Map<Integer, ExecutorService> fanOutWorkers = new ConcurrentHashMap<>(); // Layer 1
    private final Map<Integer, ExecutorService> partitionWorkers = new ConcurrentHashMap<>(); // Layer 2
    private final ScheduledExecutorService retryExecutor = Executors.newScheduledThreadPool(2); // For delayed retries

    private final int NUM_WORKERS = 10;
    private final int NUM_PARTITIONS = 10;

    public PubSubService() {
        initializeWorkers();
    }

    private void initializeWorkers() {
        // Initialize Fan-Out Workers (Layer 1)
        for (int i = 0; i < NUM_WORKERS; i++) {
            fanOutWorkers.put(i, Executors.newSingleThreadExecutor());
        }

        // Initialize Partition Workers (Layer 2)
        for (int i = 0; i < NUM_PARTITIONS; i++) {
            BlockingQueue<MessageDeliveryTask> queue = new LinkedBlockingQueue<>();
            partitionQueues.put(i, queue);
            
            ExecutorService worker = Executors.newSingleThreadExecutor();
            partitionWorkers.put(i, worker);

            // Start Consumer Loop
            worker.submit(() -> runConsumerLoop(queue));
        }
    }

    // The Consumer Loop (Runs forever in background)
    private void runConsumerLoop(BlockingQueue<MessageDeliveryTask> queue) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                MessageDeliveryTask task = queue.take(); // Blocks until message arrives
                processMessage(task);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void processMessage(MessageDeliveryTask task) {
        Subscriber subscriber = subscriberMap.get(task.getSubscriberId());
        if (subscriber == null) return;

        try {
            subscriber.receiveMessage(task.getMessage());
        } catch (Exception e) {
            handleFailure(task);
        }
    }

    // Reliability: Retry with Backoff
    private void handleFailure(MessageDeliveryTask task) {
        if (task.getRetryCount() < 3) {
            task.setRetryCount(task.getRetryCount() + 1);
            // Schedule retry after 100ms (Non-blocking)
            retryExecutor.schedule(() -> {
                // Re-route to correct partition queue
                int queueIndex = getPartitionIndex(task.getMessage(), task.getSubscriberId());
                partitionQueues.get(queueIndex).offer(task);
            }, 100, TimeUnit.MILLISECONDS);
        } else {
            // Send to Dead Letter Queue (DLQ)
            dlq.computeIfAbsent(task.getSubscriberId(), k -> new LinkedBlockingDeque<>()).offer(task);
            System.err.println("Message moved to DLQ for subscriber: " + task.getSubscriberId());
        }
    }

    // API: Publish (O(1) Latency)
    public void publishMessage(Message<String> message) {
        int workerIndex = Math.abs(message.getTopicId().hashCode() % NUM_WORKERS);

        // Async Fan-Out: Decouples Publisher from the Fan-out Loop
        fanOutWorkers.get(workerIndex).submit(() -> {
            Topic topic = topicRegistry.get(message.getTopicId());
            if (topic == null) return;

            // Simple loop (Avoid parallelStream inside single thread)
            for (String subId : topic.getSubscriberIds()) {
                int queueIndex = getPartitionIndex(message, subId);
                partitionQueues.get(queueIndex).offer(new MessageDeliveryTask(message, subId, 0));
            }
        });
    }

    private int getPartitionIndex(Message<?> message, String subscriberId) {
        String key = message.getPartitionKey() + ":" + subscriberId;
        return Hashing.consistentHash(Hashing.murmur3_128().hashString(key, StandardCharsets.UTF_8), NUM_PARTITIONS);
    }

    public void createTopic(String topicId) {
        if (topicRegistry.containsKey(topicId)) throw new RuntimeException("Topic exists");
        topicRegistry.put(topicId, new Topic(topicId, ConcurrentHashMap.newKeySet()));
    }

    public void addSubscriber(String topicId, String subscriberId) {
        if (!topicRegistry.containsKey(topicId)) throw new RuntimeException("Topic doesn't exist");
        topicRegistry.get(topicId).addSubscriber(subscriberId);
    }
    
    public void registerSubscriber(String subscriberId, Subscriber subscriber) {
        subscriberMap.put(subscriberId, subscriber);
    }

    public void shutdown() {
        fanOutWorkers.values().forEach(ExecutorService::shutdown);
        partitionWorkers.values().forEach(ExecutorService::shutdown);
        retryExecutor.shutdown();
    }
}
```

---

## 5. Interview Checklist (FAQ)

**Q: Why do you have `fanOutWorkers` AND `partitionWorkers`?**
> **A:** `fanOutWorkers` handle the "multiplication" of messages (1 msg -> 1M subscribers). `partitionWorkers` handle the actual "delivery". Separating them prevents the Publisher API from being blocked by the loop.

**Q: How do you handle a slow subscriber?**
> **A:** We use **Independent Partition Queues**. If Subscriber A is slow, it backs up `Queue 1`. Subscriber B (hashed to `Queue 2`) continues to receive messages unaffected.

**Q: Why not use `parallelStream` in `publishMessage`?**
> **A:** `parallelStream` is blocking for the calling thread. It also pollutes the global `ForkJoinPool`, risking starvation of other system tasks (e.g., DB queries).

**Q: What happens if the server crashes?**
> **A:** Since this is In-Memory, data is lost. To fix this for production, we would implement a **Write-Ahead Log (WAL)**. We would write the message to disk *before* acknowledging the publish request.
