package com.example.weatherwatch.location;

import java.time.Instant;
import java.util.UUID;

public record LocationChangedEvent(
        UUID eventId,
        long locationId,
        LocationEventType eventType,
        Instant occurredAt
) {
}
