package com.example.weatherwatch.forecast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class RedisForecastCache implements ForecastCache {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisForecastCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${weather.forecast.cache-ttl:PT10M}") Duration ttl
    ) {
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Forecast cache TTL must be positive");
        }
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
    }

    @Override
    public Optional<ForecastResponse> get(String key) {
        String cached = redis.opsForValue().get(key);
        if (cached == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(cached, ForecastResponse.class));
        } catch (JsonProcessingException exception) {
            throw new ForecastCacheException("Could not deserialize cached forecast", exception);
        }
    }

    @Override
    public void put(String key, ForecastResponse forecast) {
        try {
            String value = objectMapper.writeValueAsString(forecast);
            redis.opsForValue().set(key, value, ttl);
        } catch (JsonProcessingException exception) {
            throw new ForecastCacheException("Could not serialize forecast for caching", exception);
        }
    }

    @Override
    public void evict(String key) {
        redis.delete(key);
    }
}
