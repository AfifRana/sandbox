package com.example.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class NotificationDeduplicatorTest {

    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> valueOps;

    @Test
    void firstDeliveryReturnsTrueWhenKeyIsAbsent() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("notified:evt-1", "1", Duration.ofDays(1))).thenReturn(true);

        assertTrue(new NotificationDeduplicator(redis).firstDelivery("evt-1"));
    }

    @Test
    void replayedEventReturnsFalseWhenKeyExists() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("notified:evt-1", "1", Duration.ofDays(1))).thenReturn(false);

        assertFalse(new NotificationDeduplicator(redis).firstDelivery("evt-1"));
    }

    @Test
    void redisOutageFailsOpen() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(String.class), any(String.class), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("connection refused"));

        assertTrue(new NotificationDeduplicator(redis).firstDelivery("evt-1"));
    }
}
