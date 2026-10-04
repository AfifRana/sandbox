package com.example.weatherwatch.location;

import com.example.weatherwatch.forecast.ForecastCache;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LocationEventConsumerTests {

    @Test
    void evictsLocationForecastCacheForLocationChange() {
        ForecastCache cache = mock(ForecastCache.class);
        LocationEventConsumer consumer = new LocationEventConsumer(cache);
        LocationChangedEvent event = new LocationChangedEvent(
                UUID.randomUUID(), 42, LocationEventType.UPDATED, Instant.now());

        consumer.onLocationChanged(event);

        verify(cache).evict("forecast:location:42");
    }
}
