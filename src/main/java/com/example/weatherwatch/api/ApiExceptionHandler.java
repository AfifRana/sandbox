package com.example.weatherwatch.api;

import com.example.weatherwatch.location.LocationNotFoundException;
import com.example.weatherwatch.forecast.ForecastProviderException;
import com.example.weatherwatch.location.LocationEventPublishException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(LocationNotFoundException.class)
    ProblemDetail handleNotFound(LocationNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Location not found");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> "%s %s".formatted(error.getField(), error.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Request validation failed");
        return problem;
    }

    @ExceptionHandler(ForecastProviderException.class)
    ProblemDetail handleForecastProviderError(ForecastProviderException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, exception.getMessage());
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Forecast provider unavailable");
        return problem;
    }

    @ExceptionHandler(LocationEventPublishException.class)
    ProblemDetail handleEventPublishError(LocationEventPublishException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Location event service unavailable");
        return problem;
    }
}
