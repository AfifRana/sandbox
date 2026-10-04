package com.example.weatherwatch.location;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class LocationService {

    private final LocationRepository repository;
    private final LocationEventPublisher eventPublisher;

    public LocationService(LocationRepository repository, LocationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public LocationResponse create(LocationRequest request) {
        Location location = new Location(request.name().trim(), request.latitude(), request.longitude());
        Location saved = repository.save(location);
        eventPublisher.publish(saved.getId(), LocationEventType.CREATED);
        return LocationResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<LocationResponse> findAll() {
        return repository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(LocationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LocationResponse findById(long id) {
        return LocationResponse.from(getLocation(id));
    }

    public LocationResponse update(long id, LocationRequest request) {
        Location location = getLocation(id);
        location.update(request.name().trim(), request.latitude(), request.longitude());
        Location updated = repository.save(location);
        eventPublisher.publish(updated.getId(), LocationEventType.UPDATED);
        return LocationResponse.from(updated);
    }

    public void delete(long id) {
        Location location = getLocation(id);
        repository.delete(location);
        eventPublisher.publish(id, LocationEventType.DELETED);
    }

    private Location getLocation(long id) {
        return repository.findById(id).orElseThrow(() -> new LocationNotFoundException(id));
    }
}
