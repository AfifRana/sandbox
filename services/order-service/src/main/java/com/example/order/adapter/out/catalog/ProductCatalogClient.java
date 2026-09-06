package com.example.order.adapter.out.catalog;

import com.example.order.application.port.ProductCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Calls product-service GET /api/v1/products/{id} (public read, no token
 * needed). Retry absorbs transient network failures; the circuit breaker
 * stops hammering product-service when it is down, so order creation can
 * fail open (fallback to the client-provided price) without piling up
 * timeouts.
 */
@Component
public class ProductCatalogClient implements ProductCatalog {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ProductCatalogClient(RestClient.Builder restClientBuilder,
                                @Value("${catalog.base-url}") String baseUrl,
                                ObjectMapper objectMapper) {
        // Use the auto-configured builder so the tracing interceptor is
        // applied — the W3C traceparent header then propagates to
        // product-service and spans link up in Jaeger.
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    @Retry(name = "productCatalog", fallbackMethod = "priceForFallback")
    @CircuitBreaker(name = "productCatalog", fallbackMethod = "priceForFallback")
    public Optional<BigDecimal> priceFor(UUID productId) {
        try {
            String body = restClient.get()
                    .uri("/api/v1/products/{id}", productId)
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .retrieve()
                    .body(String.class);
            JsonNode product = objectMapper.readTree(body);
            return Optional.of(new BigDecimal(product.path("price").asText()));
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty(); // unknown product — a business 404, not a failure
        } catch (Exception e) {
            throw new ProductCatalog.CatalogUnavailableException("product catalog unreachable: " + e.getMessage(), e);
        }
    }

    /**
     * Retry + circuit-breaker fallback: when product-service is unavailable
     * after retries (or the circuit is open), rethrow so the caller decides
     * whether to fail open or closed.
     */
    @SuppressWarnings("unused")
    private Optional<BigDecimal> priceForFallback(UUID productId, Throwable t) {
        throw new ProductCatalog.CatalogUnavailableException(
                "product catalog unavailable after retries (circuit breaker fallback: " + t.getMessage() + ")", t);
    }
}
