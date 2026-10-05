package com.example.weatherwatch.forecast;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "forecast_snapshots", uniqueConstraints = {
        @UniqueConstraint(name = "forecast_snapshots_location_time_unique",
                columnNames = {"location_id", "forecast_time"})
})
public class ForecastSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location_id", nullable = false)
    private long locationId;

    @Column(name = "forecast_time", nullable = false, length = 64)
    private String forecastTime;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(nullable = false, length = 10000)
    private String payload;

    protected ForecastSnapshot() {
    }

    public ForecastSnapshot(long locationId, String forecastTime, Instant fetchedAt, String payload) {
        this.locationId = locationId;
        this.forecastTime = forecastTime;
        this.fetchedAt = fetchedAt;
        this.payload = payload;
    }

    public void replaceForecast(Instant newFetchedAt, String newPayload) {
        fetchedAt = newFetchedAt;
        payload = newPayload;
    }

    public Long getId() {
        return id;
    }

    public long getLocationId() {
        return locationId;
    }

    public String getForecastTime() {
        return forecastTime;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public String getPayload() {
        return payload;
    }
}
