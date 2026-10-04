package com.example.weatherwatch.forecast;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

public class OpenMeteoClient {

    private static final String CURRENT_VARIABLES = String.join(",",
            "temperature_2m",
            "relative_humidity_2m",
            "apparent_temperature",
            "is_day",
            "precipitation",
            "rain",
            "showers",
            "snowfall",
            "weather_code",
            "cloud_cover",
            "pressure_msl",
            "surface_pressure",
            "wind_speed_10m",
            "wind_direction_10m",
            "wind_gusts_10m"
    );

    private final RestClient restClient;

    public OpenMeteoClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public ForecastResponse getCurrentForecast(double latitude, double longitude) {
        try {
            ForecastResponse forecast = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/v1/forecast")
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current", CURRENT_VARIABLES)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new ForecastProviderException(
                                "Open-Meteo returned HTTP %d".formatted(response.getStatusCode().value()));
                    })
                    .body(ForecastResponse.class);
            if (forecast == null) {
                throw new ForecastProviderException("Open-Meteo returned an empty forecast");
            }
            return forecast;
        } catch (RestClientResponseException | ResourceAccessException exception) {
            throw new ForecastProviderException("Could not retrieve forecast from Open-Meteo", exception);
        }
    }
}
