package com.example.weatherwatch.forecast;

public class ForecastProviderException extends RuntimeException {

    public ForecastProviderException(String message) {
        super(message);
    }

    public ForecastProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
