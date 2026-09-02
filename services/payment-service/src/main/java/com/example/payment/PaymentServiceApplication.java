package com.example.payment;

import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@SpringBootApplication
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}

/**
 * Consumes order events. Idempotency note: consumers must deduplicate by
 * orderId since Kafka gives at-least-once delivery.
 */
@Component
class OrderEventListener {

    @KafkaListener(topics = "order-events", groupId = "payment-service")
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        // TODO: process payment (Strategy pattern per payment method), emit payment.paid event
        System.out.printf("Processing payment for order %s%n", record.key());
    }
}
