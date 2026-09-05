package com.example.payment.adapter.in.web;

import com.example.payment.application.ProcessPaymentUseCase;
import com.example.payment.application.strategy.PaymentDeclinedException;
import com.example.payment.domain.Payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final ProcessPaymentUseCase processPayment;

    public PaymentController(ProcessPaymentUseCase processPayment) {
        this.processPayment = processPayment;
    }

    @PostMapping
    public ResponseEntity<Payment> create(@Valid @RequestBody CreatePaymentRequest request) {
        Payment payment = processPayment.process(request.orderId(), request.customerId(),
                request.amount(), request.method());
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    public record CreatePaymentRequest(
            @NotNull UUID orderId,
            @NotNull UUID customerId,
            @NotNull @Positive BigDecimal amount,
            @NotNull Payment.PaymentMethod method) {}

    @ExceptionHandler(PaymentDeclinedException.class)
    public ProblemDetail handleDeclined(PaymentDeclinedException ex) {
        // 402 Payment Required — semantically the right code for a declined charge
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYMENT_REQUIRED, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
