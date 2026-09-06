package com.example.auth.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Demo user store. A real deployment would delegate to an identity provider
 * (Keycloak, Azure AD, ...) or a hashed-password table; the token issuing
 * contract stays the same.
 */
public class UserService {

    private final Map<String, User> users;

    public UserService() {
        this.users = Map.of(
                "alice", new User("alice", "password", List.of("CUSTOMER")),
                "bob", new User("bob", "password", List.of("ADMIN")),
                "carol", new User("carol", "password", List.of("CUSTOMER", "ADMIN")));
    }

    public Optional<User> authenticate(String username, String password) {
        return Optional.ofNullable(users.get(username))
                .filter(u -> u.password().equals(password));
    }

    public record User(String username, String password, List<String> roles) {}
}
