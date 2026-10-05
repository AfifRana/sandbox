package com.example.weatherwatch.forecast;

import com.example.weatherwatch.location.Location;
import com.example.weatherwatch.location.LocationNotFoundException;
import com.example.weatherwatch.location.LocationRepository;
import org.springframework.stereotype.Service;

@Service
public class ForecastService {

    private final LocationRepository locationRepository;
    private final ForecastCache forecastCache;
    private final OpenMeteoClient openMeteoClient;

    public ForecastService(
            LocationRepository locationRepository,
            ForecastCache forecastCache,
            OpenMeteoClient openMeteoClient
    ) {
        this.locationRepository = locationRepository;
        this.forecastCache = forecastCache;
        this.openMeteoClient = openMeteoClient;
    }

    public ForecastResponse getForecast(long locationId) {
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new LocationNotFoundException(locationId));
        String cacheKey = ForecastCacheKeys.forLocation(location.getId());

        return forecastCache.get(cacheKey)
                .orElseGet(() -> fetchAndCache(cacheKey, location));
    }

    private ForecastResponse fetchAndCache(String cacheKey, Location location) {
        ForecastResponse forecast = openMeteoClient.getCurrentForecast(
                location.getLatitude(), location.getLongitude());
        forecastCache.put(cacheKey, forecast);
        return forecast;
    }
}
