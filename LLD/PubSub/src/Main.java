import service.PubSubService;
import models.message.TextMessage;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Pub-Sub System Demo ===\n");
        
        // Initialize service
        PubSubService pubSubService = new PubSubService();
        
        // Create topic
        pubSubService.createTopic("orders");
        System.out.println("✓ Created topic: orders\n");
        
        // Register subscribers
        pubSubService.registerSubscriber("subscriber-1");
        pubSubService.registerSubscriber("subscriber-2");
        pubSubService.registerSubscriber("subscriber-3");
        System.out.println("✓ Registered 3 subscribers\n");
        
        // Add subscribers to topic
        pubSubService.addSubscriber("orders", "subscriber-1");
        pubSubService.addSubscriber("orders", "subscriber-2");
        pubSubService.addSubscriber("orders", "subscriber-3");
        System.out.println("✓ Added subscribers to topic\n");
        
        // Publish messages
        System.out.println("Publishing messages...\n");
        
        TextMessage msg1 = new TextMessage("msg-1", "orders", "order-123", "Order created");
        TextMessage msg2 = new TextMessage("msg-2", "orders", "order-123", "Payment received");
        TextMessage msg3 = new TextMessage("msg-3", "orders", "order-456", "Order shipped");
        
        pubSubService.publishMessage(msg1);
        pubSubService.publishMessage(msg2);
        pubSubService.publishMessage(msg3);
        
        System.out.println("✓ Published 3 messages\n");
        
        // Wait for processing
        Thread.sleep(2000);
        
        System.out.println("\n=== Shutting down ===");
        pubSubService.shutdown();
        System.out.println("✓ Shutdown complete");
    }
}