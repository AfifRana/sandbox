package com.example.notification;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}

@Component
class OrderEventNotifier {

    @KafkaListener(topics = "order-events", groupId = "notification-service")
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        // TODO: send email/push notification
        System.out.printf("Notifying customer about order %s%n", record.key());
    }
}
