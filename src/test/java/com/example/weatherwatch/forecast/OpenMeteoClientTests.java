package com.example.weatherwatch.forecast;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

class OpenMeteoClientTests {

    private MockRestServiceServer server;
    private OpenMeteoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.open-meteo.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenMeteoClient(builder.build());
    }

    @Test
    void callsCurrentForecastEndpointAndMapsResponse() {
        server.expect(requestTo(startsWith("https://api.open-meteo.test/v1/forecast?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("latitude", "-6.2"))
                .andExpect(queryParam("longitude", "106.8"))
                .andExpect(queryParam("current",
                        "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,rain,showers,snowfall,weather_code,cloud_cover,pressure_msl,surface_pressure,wind_speed_10m,wind_direction_10m,wind_gusts_10m"))
                .andRespond(withSuccess("""
                        {
                          "latitude": -6.2,
                          "longitude": 106.8,
                          "generationtime_ms": 0.12,
                          "utc_offset_seconds": 25200,
                          "timezone": "Asia/Jakarta",
                          "timezone_abbreviation": "WIB",
                          "elevation": 8.0,
                          "current_units": {
                            "time": "iso8601",
                            "interval": "seconds",
                            "temperature_2m": "°C",
                            "relative_humidity_2m": "%",
                            "apparent_temperature": "°C",
                            "is_day": "",
                            "precipitation": "mm",
                            "rain": "mm",
                            "showers": "mm",
                            "snowfall": "cm",
                            "weather_code": "wmo code",
                            "cloud_cover": "%",
                            "pressure_msl": "hPa",
                            "surface_pressure": "hPa",
                            "wind_speed_10m": "km/h",
                            "wind_direction_10m": "°",
                            "wind_gusts_10m": "km/h"
                          },
                          "current": {
                            "time": "2026-10-04T15:00",
                            "interval": 900,
                            "temperature_2m": 30.1,
                            "relative_humidity_2m": 70,
                            "apparent_temperature": 34.2,
                            "is_day": 1,
                            "precipitation": 0.2,
                            "rain": 0,
                            "showers": 0,
                            "snowfall": 0,
                            "weather_code": 3,
                            "cloud_cover": 50,
                            "pressure_msl": 1008.1,
                            "surface_pressure": 1007.2,
                            "wind_speed_10m": 10.5,
                            "wind_direction_10m": 180,
                            "wind_gusts_10m": 15.4
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ForecastResponse response = client.getCurrentForecast(-6.2, 106.8);

        assertThat(response.timezone()).isEqualTo("Asia/Jakarta");
        assertThat(response.current().temperature2m()).isEqualTo(30.1);
        assertThat(response.currentUnits().temperature2m()).isEqualTo("°C");
        server.verify();
    }

    @Test
    void reportsProviderServerFailure() {
        server.expect(requestTo(startsWith("https://api.open-meteo.test/v1/forecast?")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.getCurrentForecast(0, 0))
                .isInstanceOf(ForecastProviderException.class)
                .hasMessageContaining("HTTP 500");
        server.verify();
    }
}
