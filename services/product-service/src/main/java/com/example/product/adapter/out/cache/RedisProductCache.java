package com.example.product.adapter.out.cache;

import com.example.product.application.port.ProductCache;
import com.example.product.domain.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis cache-aside adapter. Entries expire after a TTL so stale data is
 * bounded even if an eviction is ever missed.
 */
@Component
public class RedisProductCache implements ProductCache {

    private static final String KEY_PREFIX = "product:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisProductCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<Product> get(UUID id) {
        String json = redis.opsForValue().get(key(id));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, Product.class));
        } catch (Exception e) {
            // Corrupt cache entry: evict and treat as a miss
            evict(id);
            return Optional.empty();
        }
    }

    @Override
    public void put(Product product) {
        try {
            redis.opsForValue().set(key(product.id()), objectMapper.writeValueAsString(product), TTL);
        } catch (Exception e) {
            // Cache write failure must not fail the request — degrade to DB reads
        }
    }

    @Override
    public void evict(UUID id) {
        redis.delete(key(id));
    }

    private String key(UUID id) {
        return KEY_PREFIX + id;
    }
}
