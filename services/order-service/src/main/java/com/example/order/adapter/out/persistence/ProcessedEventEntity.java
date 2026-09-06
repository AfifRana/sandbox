package com.example.order.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEventId.class)
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    UUID eventId;

    @Id
    @Column(name = "consumer_group", nullable = false, length = 50)
    String consumerGroup;

    @Column(name = "processed_at", nullable = false)
    Instant processedAt;
}
