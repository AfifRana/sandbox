package com.example.weatherwatch.forecast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ForecastResponse(
        double latitude,
        double longitude,
        double generationtimeMs,
        int utcOffsetSeconds,
        String timezone,
        String timezoneAbbreviation,
        double elevation,
        CurrentWeather current,
        CurrentWeatherUnits currentUnits
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CurrentWeather(
            String time,
            int interval,
            @JsonProperty("temperature_2m")
            double temperature2m,
            @JsonProperty("relative_humidity_2m")
            int relativeHumidity2m,
            @JsonProperty("apparent_temperature")
            double apparentTemperature,
            @JsonProperty("is_day")
            int isDay,
            double precipitation,
            double rain,
            double showers,
            double snowfall,
            @JsonProperty("weather_code")
            int weatherCode,
            int cloudCover,
            @JsonProperty("pressure_msl")
            double pressureMsl,
            @JsonProperty("surface_pressure")
            double surfacePressure,
            @JsonProperty("wind_speed_10m")
            double windSpeed10m,
            @JsonProperty("wind_direction_10m")
            int windDirection10m,
            @JsonProperty("wind_gusts_10m")
            double windGusts10m
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CurrentWeatherUnits(
            String time,
            String interval,
            @JsonProperty("temperature_2m")
            String temperature2m,
            @JsonProperty("relative_humidity_2m")
            String relativeHumidity2m,
            @JsonProperty("apparent_temperature")
            String apparentTemperature,
            @JsonProperty("is_day")
            String isDay,
            String precipitation,
            String rain,
            String showers,
            String snowfall,
            @JsonProperty("weather_code")
            String weatherCode,
            String cloudCover,
            @JsonProperty("pressure_msl")
            String pressureMsl,
            @JsonProperty("surface_pressure")
            String surfacePressure,
            @JsonProperty("wind_speed_10m")
            String windSpeed10m,
            @JsonProperty("wind_direction_10m")
            String windDirection10m,
            @JsonProperty("wind_gusts_10m")
            String windGusts10m
    ) {
    }
}
