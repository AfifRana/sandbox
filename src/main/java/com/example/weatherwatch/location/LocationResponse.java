package com.example.weatherwatch.location;

import java.time.Instant;

public record LocationResponse(
        Long id,
        String name,
        double latitude,
        double longitude,
        Instant createdAt,
        Instant updatedAt
) {
    static LocationResponse from(Location location) {
        return new LocationResponse(
                location.getId(),
                location.getName(),
                location.getLatitude(),
                location.getLongitude(),
                location.getCreatedAt(),
                location.getUpdatedAt()
        );
    }
}
