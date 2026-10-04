package com.example.weatherwatch.location;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class LocationService {

    private final LocationRepository repository;

    public LocationService(LocationRepository repository) {
        this.repository = repository;
    }

    public LocationResponse create(LocationRequest request) {
        Location location = new Location(request.name().trim(), request.latitude(), request.longitude());
        return LocationResponse.from(repository.save(location));
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
        return LocationResponse.from(repository.save(location));
    }

    public void delete(long id) {
        repository.delete(getLocation(id));
    }

    private Location getLocation(long id) {
        return repository.findById(id).orElseThrow(() -> new LocationNotFoundException(id));
    }
}
