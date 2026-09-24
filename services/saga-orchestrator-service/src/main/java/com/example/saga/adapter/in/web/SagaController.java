package com.example.saga.adapter.in.web;

import com.example.saga.application.OrderSagaUseCase;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saga start endpoint. In the demo the gateway/order-service flow posts here
 * to kick off an orchestrated purchase; the endpoint returns the sagaId for
 * tracing the resulting inventory/fulfillment replies.
 */
@RestController
@RequestMapping("/api/v1/sagas")
public class SagaController {

    private final OrderSagaUseCase useCase;

    public SagaController(OrderSagaUseCase useCase) {
        this.useCase = useCase;
    }

    public record StartSagaRequest(UUID orderId, Map<UUID, Integer> lines) {}

    @PostMapping
    public ResponseEntity<Map<String, Object>> start(@RequestBody StartSagaRequest request) {
        UUID sagaId = useCase.start(request.orderId(), request.lines());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sagaId", sagaId.toString());
        body.put("orderId", request.orderId().toString());
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}