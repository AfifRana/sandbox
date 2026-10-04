package com.example.weatherwatch.location;

public class LocationNotFoundException extends RuntimeException {

    public LocationNotFoundException(long id) {
        super("Location %d was not found".formatted(id));
    }
}
