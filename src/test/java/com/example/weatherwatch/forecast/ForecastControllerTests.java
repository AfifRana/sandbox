package com.example.weatherwatch.forecast;

import com.example.weatherwatch.location.Location;
import com.example.weatherwatch.location.LocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:weather;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.kafka.listener.auto-startup=false",
        "weather.kafka.create-topic=false"
})
class ForecastControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LocationRepository locationRepository;

    @MockitoBean
    private ForecastCache forecastCache;

    @MockitoBean
    private OpenMeteoClient openMeteoClient;

    private long locationId;

    @BeforeEach
    void setUp() {
        locationRepository.deleteAll();
        locationId = locationRepository.save(new Location("Jakarta", -6.2, 106.8)).getId();
        clearInvocations(forecastCache, openMeteoClient);
    }

    @Test
    void fetchesAndCachesForecastOnCacheMiss() throws Exception {
        ForecastResponse forecast = sampleForecast();
        when(forecastCache.get(anyString())).thenReturn(Optional.empty());
        when(openMeteoClient.getCurrentForecast(-6.2, 106.8)).thenReturn(forecast);

        mockMvc.perform(get("/api/locations/{id}/forecast", locationId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(org.springframework.http.MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.timezone").value("Asia/Jakarta"))
                .andExpect(jsonPath("$.current.temperature_2m").value(30.1))
                .andExpect(jsonPath("$.current_units.temperature_2m").value("°C"));

        verify(forecastCache).put(anyString(), org.mockito.ArgumentMatchers.eq(forecast));
        verify(openMeteoClient).getCurrentForecast(-6.2, 106.8);
    }

    @Test
    void returnsCachedForecastWithoutCallingProvider() throws Exception {
        ForecastResponse forecast = sampleForecast();
        when(forecastCache.get(anyString())).thenReturn(Optional.of(forecast));

        mockMvc.perform(get("/api/locations/{id}/forecast", locationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.temperature_2m").value(30.1));

        verify(openMeteoClient, never()).getCurrentForecast(-6.2, 106.8);
        verify(forecastCache, never()).put(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void reportsProviderFailureAsBadGatewayAndDoesNotCacheIt() throws Exception {
        when(forecastCache.get(anyString())).thenReturn(Optional.empty());
        when(openMeteoClient.getCurrentForecast(-6.2, 106.8))
                .thenThrow(new ForecastProviderException("Open-Meteo is unavailable"));

        mockMvc.perform(get("/api/locations/{id}/forecast", locationId))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.title").value("Forecast provider unavailable"));

        verify(forecastCache, never()).put(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsNotFoundWithoutCallingCacheOrProvider() throws Exception {
        mockMvc.perform(get("/api/locations/987654/forecast"))
                .andExpect(status().isNotFound());

        verify(forecastCache, never()).get(anyString());
        verify(openMeteoClient, never()).getCurrentForecast(org.mockito.ArgumentMatchers.anyDouble(),
                org.mockito.ArgumentMatchers.anyDouble());
    }

    private static ForecastResponse sampleForecast() {
        return new ForecastResponse(
                -6.2,
                106.8,
                0.123,
                25200,
                "Asia/Jakarta",
                "WIB",
                8,
                new ForecastResponse.CurrentWeather(
                        "2026-10-04T15:00",
                        900,
                        30.1,
                        70,
                        34.2,
                        1,
                        0.2,
                        0,
                        0,
                        0,
                        3,
                        50,
                        1008.1,
                        1007.2,
                        10.5,
                        180,
                        15.4
                ),
                new ForecastResponse.CurrentWeatherUnits(
                        "iso8601",
                        "seconds",
                        "°C",
                        "%",
                        "°C",
                        "",
                        "mm",
                        "mm",
                        "mm",
                        "cm",
                        "wmo code",
                        "%",
                        "hPa",
                        "hPa",
                        "km/h",
                        "°",
                        "km/h"
                )
        );
    }
}
