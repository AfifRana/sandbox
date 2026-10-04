package com.example.weatherwatch.forecast;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class OpenMeteoConfiguration {

    @Bean
    OpenMeteoClient openMeteoClient(
            RestClient.Builder builder,
            @Value("${weather.open-meteo.base-url:https://api.open-meteo.com}") String baseUrl,
            @Value("${weather.open-meteo.connect-timeout:3s}") Duration connectTimeout,
            @Value("${weather.open-meteo.read-timeout:10s}") Duration readTimeout
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        RestClient restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
        return new OpenMeteoClient(restClient);
    }
}
