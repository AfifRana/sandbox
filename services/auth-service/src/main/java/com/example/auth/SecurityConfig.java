package com.example.auth;

import com.example.auth.application.TokenService;
import com.example.auth.application.UserService;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public TokenService tokenService(@Value("${app.auth.issuer}") String issuer,
                                     @Value("${app.auth.access-token-ttl}") Duration ttl) {
        KeyPair pair = generateKeyPair();
        return new TokenService(
                (RSAPrivateKey) pair.getPrivate(),
                (RSAPublicKey) pair.getPublic(),
                UUID.randomUUID().toString(),
                issuer,
                ttl);
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA not available", e);
        }
    }

    @Bean
    public UserService userService() {
        return new UserService();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // auth-service itself only exposes public endpoints (login + JWKS);
        // actuator health is used by orchestrators.
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/auth/**", "/oauth2/jwks", "/actuator/**").permitAll()
                    .anyRequest().denyAll())
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
