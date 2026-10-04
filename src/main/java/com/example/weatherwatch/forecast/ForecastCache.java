package com.example.weatherwatch.forecast;

import java.util.Optional;

public interface ForecastCache {

    Optional<ForecastResponse> get(String key);

    void put(String key, ForecastResponse forecast);

    void evict(String key);
}
