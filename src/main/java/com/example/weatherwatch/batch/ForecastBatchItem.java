package com.example.weatherwatch.batch;

import com.example.weatherwatch.forecast.ForecastResponse;
import com.example.weatherwatch.forecast.ForecastSnapshot;

public record ForecastBatchItem(ForecastSnapshot snapshot, ForecastResponse forecast) {
}
