package com.example.notification;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis SETNX-based dedupe for at-least-once Kafka delivery. Cheaper than a
 * DB inbox and TTL-bounded, which suits non-critical side effects like
 * notifications: a duplicate after TTL expiry is an acceptable rare event,
 * whereas losing notifications during a Redis outage is not — hence fail-open.
 */
@Component
public class NotificationDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(NotificationDeduplicator.class);
    private static final Duration TTL = Duration.ofDays(1);

    private final StringRedisTemplate redis;

    public NotificationDeduplicator(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * @return true if this event should be processed (first delivery)
     */
    public boolean firstDelivery(String eventId) {
        String key = "notified:" + eventId;
        try {
            Boolean first = redis.opsForValue().setIfAbsent(key, "1", TTL);
            return Boolean.TRUE.equals(first);
        } catch (Exception e) {
            log.warn("Redis unavailable, allowing possible duplicate for event {}: {}",
                    eventId, e.getMessage());
            return true; // fail-open: prefer a duplicate notification over a lost one
        }
    }
}
