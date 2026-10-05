package com.example.weatherwatch.forecast;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ForecastSnapshotRepository extends JpaRepository<ForecastSnapshot, Long> {

    Optional<ForecastSnapshot> findByLocationIdAndForecastTime(long locationId, String forecastTime);
}
