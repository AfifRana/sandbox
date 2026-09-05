package com.example.payment.adapter.out.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_outbox_events")
public class PaymentOutboxEntry {

    @Id
    UUID id;

    @Column(name = "aggregate_id", nullable = false)
    UUID aggregateId;

    @Column(nullable = false, length = 50)
    String type;

    @Column(nullable = false, length = 4000)
    String payload;

    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @Column(nullable = false)
    boolean published;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getAggregateId() { return aggregateId; }
    public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }
}
