package com.example.weatherwatch.location;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class LocationEventPublisher {

    private final KafkaTemplate<String, LocationChangedEvent> kafkaTemplate;
    private final String topic;
    private final Duration sendTimeout;

    public LocationEventPublisher(
            KafkaTemplate<String, LocationChangedEvent> kafkaTemplate,
            @Value("${weather.kafka.location-events-topic:location-events}") String topic,
            @Value("${weather.kafka.send-timeout:5s}") Duration sendTimeout
    ) {
        if (sendTimeout.isZero() || sendTimeout.isNegative()) {
            throw new IllegalArgumentException("Kafka send timeout must be positive");
        }
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.sendTimeout = sendTimeout;
    }

    public void publish(long locationId, LocationEventType eventType) {
        LocationChangedEvent event = new LocationChangedEvent(
                UUID.randomUUID(), locationId, eventType, Instant.now());
        try {
            kafkaTemplate.send(topic, Long.toString(locationId), event)
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LocationEventPublishException("Interrupted while publishing location event", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new LocationEventPublishException("Could not publish location event", exception);
        } catch (RuntimeException exception) {
            throw new LocationEventPublishException("Could not publish location event", exception);
        }
    }
}
