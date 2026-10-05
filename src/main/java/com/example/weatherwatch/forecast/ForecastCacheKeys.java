package com.example.weatherwatch.forecast;

public final class ForecastCacheKeys {

    private ForecastCacheKeys() {
    }

    public static String forLocation(long locationId) {
        return "forecast:location:%d".formatted(locationId);
    }
}
