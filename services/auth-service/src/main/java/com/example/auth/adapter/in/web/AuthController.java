package com.example.auth.adapter.in.web;

import com.example.auth.application.TokenService;
import com.example.auth.application.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService users;
    private final TokenService tokens;

    public AuthController(UserService users, TokenService tokens) {
        this.users = users;
        this.tokens = tokens;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        UserService.User user = users.authenticate(request.username(), request.password())
                .orElseThrow(InvalidCredentials::new);
        String token = tokens.issueToken(user.username(), user.roles());
        return ResponseEntity.ok(new LoginResponse(token, "Bearer", user.roles()));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record LoginResponse(String accessToken, String tokenType, List<String> roles) {}

    public static class InvalidCredentials extends RuntimeException {}

    @ExceptionHandler(InvalidCredentials.class)
    public ProblemDetail handleInvalidCredentials() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password");
    }
}
