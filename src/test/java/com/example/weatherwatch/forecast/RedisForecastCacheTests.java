package com.example.weatherwatch.forecast;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisForecastCacheTests {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private RedisForecastCache cache;

    @BeforeEach
    void setUp() {
        cache = new RedisForecastCache(redis, new ObjectMapper(), Duration.ofMinutes(5));
    }

    @Test
    void returnsEmptyForMiss() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("forecast:test")).thenReturn(null);

        assertThat(cache.get("forecast:test")).isEmpty();
    }

    @Test
    void evictsLocationForecastKey() {
        cache.evict("forecast:location:42");

        verify(redis).delete("forecast:location:42");
    }

    @Test
    void serializesForecastAndWritesConfiguredTtl() {
        when(redis.opsForValue()).thenReturn(values);
        ForecastResponse forecast = new ForecastResponse(
                0,
                0,
                0.1,
                0,
                "GMT",
                "GMT",
                0,
                new ForecastResponse.CurrentWeather(
                        "2026-10-04T00:00", 900, 20, 50, 20, 1,
                        0, 0, 0, 0, 0, 0, 1013, 1013, 0, 0, 0),
                new ForecastResponse.CurrentWeatherUnits(
                        "iso8601", "seconds", "°C", "%", "°C", "",
                        "mm", "mm", "mm", "cm", "wmo code", "%",
                        "hPa", "hPa", "km/h", "°", "km/h")
        );
        cache.put("forecast:test", forecast);

        verify(values).set("forecast:test", new ObjectMapper().valueToTree(forecast).toString(),
                Duration.ofMinutes(5));
    }

    @Test
    void deserializesCachedForecast() throws Exception {
        when(redis.opsForValue()).thenReturn(values);
        ForecastResponse expected = new ForecastResponse(
                0,
                0,
                0.1,
                0,
                "GMT",
                "GMT",
                0,
                new ForecastResponse.CurrentWeather(
                        "2026-10-04T00:00", 900, 20, 50, 20, 1,
                        0, 0, 0, 0, 0, 0, 1013, 1013, 0, 0, 0),
                new ForecastResponse.CurrentWeatherUnits(
                        "iso8601", "seconds", "°C", "%", "°C", "",
                        "mm", "mm", "mm", "cm", "wmo code", "%",
                        "hPa", "hPa", "km/h", "°", "km/h")
        );
        when(values.get("forecast:test")).thenReturn(new ObjectMapper().writeValueAsString(expected));

        Optional<ForecastResponse> actual = cache.get("forecast:test");

        assertThat(actual).contains(expected);
    }
}
