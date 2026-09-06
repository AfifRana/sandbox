package com.example.order.application.port;

import java.util.UUID;

/**
 * Transactional inbox: records which events a consumer group has already
 * processed. Implementations must participate in the caller's transaction so
 * the claim and the state change commit (or roll back) atomically.
 */
public interface ProcessedEventStore {

    /**
     * @return true if this is the first time the event is claimed for the group
     */
    boolean claim(UUID eventId, String consumerGroup);
}
