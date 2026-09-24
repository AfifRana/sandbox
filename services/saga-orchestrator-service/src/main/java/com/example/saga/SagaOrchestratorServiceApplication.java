package com.example.saga;

import com.example.saga.application.OrderSagaUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableScheduling
public class SagaOrchestratorServiceApplication {

    @Autowired
    @Lazy
    private OrderSagaUseCase useCase;

    public static void main(String[] args) {
        SpringApplication.run(SagaOrchestratorServiceApplication.class, args);
    }

    /**
     * Restart recovery: re-issue pending commands once on boot, then sweep
     * periodically so a lost reply (timeout, crash between outbox write and
     * Kafka publish) self-heals. Participants deduplicate by sagaId, so the
     * re-issued commands are safe replays.
     */
    @Bean
    ApplicationRunner recoverOnStartup() {
        return args -> useCase.recoverPending();
    }

    @Scheduled(fixedDelay = 30_000)
    void recoverPeriodically() {
        useCase.recoverPending();
    }
}

