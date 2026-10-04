package com.example.weatherwatch.location;

import com.example.weatherwatch.forecast.ForecastCache;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class LocationEventConsumer {

    private final ForecastCache forecastCache;

    public LocationEventConsumer(ForecastCache forecastCache) {
        this.forecastCache = forecastCache;
    }

    @KafkaListener(
            topics = "${weather.kafka.location-events-topic:location-events}",
            groupId = "${spring.kafka.consumer.group-id:weather-watch}"
    )
    public void onLocationChanged(LocationChangedEvent event) {
        forecastCache.evict(cacheKey(event.locationId()));
    }

    private String cacheKey(long locationId) {
        return "forecast:location:%d".formatted(locationId);
    }
}
