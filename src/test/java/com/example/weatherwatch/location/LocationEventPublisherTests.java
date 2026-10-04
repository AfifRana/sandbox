package com.example.weatherwatch.location;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocationEventPublisherTests {

    @Test
    void publishesLocationEventKeyedByLocationId() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, LocationChangedEvent> kafka = mock(KafkaTemplate.class);
        when(kafka.send(eq("location-events"), eq("42"), any(LocationChangedEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(null));
        LocationEventPublisher publisher = new LocationEventPublisher(
                kafka, "location-events", java.time.Duration.ofSeconds(1));

        publisher.publish(42, LocationEventType.UPDATED);

        verify(kafka).send(eq("location-events"), eq("42"), any(LocationChangedEvent.class));
    }

    @Test
    void surfacesKafkaSendFailure() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, LocationChangedEvent> kafka = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, LocationChangedEvent>> failedSend =
                CompletableFuture.failedFuture(new IllegalStateException("broker unavailable"));
        when(kafka.send(eq("location-events"), eq("42"), any(LocationChangedEvent.class)))
                .thenReturn(failedSend);
        LocationEventPublisher publisher = new LocationEventPublisher(
                kafka, "location-events", java.time.Duration.ofSeconds(1));

        assertThatThrownBy(() -> publisher.publish(42, LocationEventType.DELETED))
                .isInstanceOf(LocationEventPublishException.class)
                .hasMessageContaining("Could not publish location event");
    }

    @Test
    void surfacesSynchronousKafkaFailure() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, LocationChangedEvent> kafka = mock(KafkaTemplate.class);
        when(kafka.send(eq("location-events"), eq("42"), any(LocationChangedEvent.class)))
                .thenThrow(new IllegalStateException("broker unavailable"));
        LocationEventPublisher publisher = new LocationEventPublisher(
                kafka, "location-events", java.time.Duration.ofSeconds(1));

        assertThatThrownBy(() -> publisher.publish(42, LocationEventType.CREATED))
                .isInstanceOf(LocationEventPublishException.class)
                .hasMessageContaining("Could not publish location event");
    }
}
