package com.example.saga.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.saga.application.port.SagaCommandPublisher;
import com.example.saga.application.port.SagaProcessRepository;
import com.example.saga.domain.SagaProcess;
import com.example.saga.domain.SagaStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Test-first edge-case policy (Saga milestone): the invariant under test is
 * that every completed step of a saga is either confirmed or compensated —
 * duplicate step events, fulfillment failure after payment, and restart
 * recovery must each produce exactly one effective outcome.
 */
class OrderSagaUseCaseTest {

    private InMemorySagaRepository repository;
    private RecordingCommandPublisher publisher;
    private OrderSagaUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = new InMemorySagaRepository();
        publisher = new RecordingCommandPublisher();
        useCase = new OrderSagaUseCase(repository, publisher);
    }

    @Test
    void completesWhenAllStepsSucceed() {
        UUID sagaId = useCase.start(orderId(), lines("22222222-2222-2222-2222-222222222222", 2));

        assertThat(repository.find(sagaId).orElseThrow().status()).isEqualTo(SagaStatus.STARTED);
        assertThat(publisher.commands).containsExactly("inventory.reserve:" + sagaId);

        useCase.onInventoryReserved(sagaId);
        assertThat(repository.find(sagaId).orElseThrow().status()).isEqualTo(SagaStatus.INVENTORY_RESERVED);
        assertThat(publisher.commands).contains("fulfillment.ship:" + sagaId);

        useCase.onFulfillmentShipped(sagaId);
        assertThat(repository.find(sagaId).orElseThrow().status()).isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    void rejectedReservationFailsSagaWithoutShipping() {
        UUID sagaId = useCase.start(orderId(), lines("66666666-6666-6666-6666-666666666666", 1));

        useCase.onInventoryRejected(sagaId, "insufficient stock");

        SagaProcess saga = repository.find(sagaId).orElseThrow();
        assertThat(saga.status()).isEqualTo(SagaStatus.REJECTED);
        assertThat(saga.reason()).isEqualTo("insufficient stock");
        assertThat(publisher.commands).doesNotContain("fulfillment.ship:" + sagaId);
        assertThat(publisher.commands).doesNotContain("inventory.release:" + sagaId);
    }

    @Test
    void fulfillmentFailureAfterReservationCompensatesInventory() {
        UUID sagaId = useCase.start(orderId(), lines("77777777-7777-7777-7777-777777777777", 3));
        useCase.onInventoryReserved(sagaId);

        useCase.onFulfillmentFailed(sagaId, "carrier rejected shipment");

        SagaProcess saga = repository.find(sagaId).orElseThrow();
        assertThat(saga.status()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(publisher.commands).contains("inventory.release:" + sagaId);
    }

    @Test
    void duplicateInventoryReservedEventIsIdempotent() {
        UUID sagaId = useCase.start(orderId(), lines("22222222-2222-2222-2222-222222222222", 1));
        int shipCommandsBefore = countCommands("fulfillment.ship:" + sagaId);

        useCase.onInventoryReserved(sagaId);
        useCase.onInventoryReserved(sagaId);
        useCase.onInventoryReserved(sagaId);

        assertThat(countCommands("fulfillment.ship:" + sagaId) - shipCommandsBefore).isEqualTo(1);
    }

    @Test
    void duplicateFulfillmentShippedEventIsIdempotent() {
        UUID sagaId = useCase.start(orderId(), lines("22222222-2222-2222-2222-222222222222", 1));
        useCase.onInventoryReserved(sagaId);

        useCase.onFulfillmentShipped(sagaId);
        useCase.onFulfillmentShipped(sagaId);

        assertThat(repository.find(sagaId).orElseThrow().status()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(countCommands("inventory.release:" + sagaId)).isZero();
    }

    @Test
    void compensationReleaseIsSentOnlyOnce() {
        UUID sagaId = useCase.start(orderId(), lines("77777777-7777-7777-7777-777777777777", 1));
        useCase.onInventoryReserved(sagaId);

        useCase.onFulfillmentFailed(sagaId, "carrier rejected shipment");
        useCase.onFulfillmentFailed(sagaId, "carrier rejected shipment");

        assertThat(countCommands("inventory.release:" + sagaId)).isEqualTo(1);
    }

    @Test
    void unknownSagaEventsAreIgnored() {
        UUID unknown = UUID.randomUUID();
        useCase.onInventoryReserved(unknown);
        useCase.onInventoryRejected(unknown, "boom");
        useCase.onFulfillmentShipped(unknown);
        useCase.onFulfillmentFailed(unknown, "boom");
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void recoveryReissuesPendingCommandAfterRestart() {
        UUID sagaId = useCase.start(orderId(), lines("22222222-2222-2222-2222-222222222222", 1));
        repository.clearPublisherCommands(publisher);

        // Simulate a restart: a fresh use-case over the same durable repository
        // must re-issue the still-pending reserve command.
        OrderSagaUseCase restarted = new OrderSagaUseCase(repository, publisher);
        restarted.recoverPending();

        assertThat(countCommands("inventory.reserve:" + sagaId)).isEqualTo(1);
    }

    @Test
    void terminalSagasAreNotRecovered() {
        UUID sagaId = useCase.start(orderId(), lines("22222222-2222-2222-2222-222222222222", 1));
        useCase.onInventoryReserved(sagaId);
        useCase.onFulfillmentShipped(sagaId);
        repository.clearPublisherCommands(publisher);

        OrderSagaUseCase restarted = new OrderSagaUseCase(repository, publisher);
        restarted.recoverPending();

        assertThat(publisher.commands).isEmpty();
    }

    private int countCommands(String command) {
        return (int) publisher.commands.stream().filter(c -> c.equals(command)).count();
    }

    private UUID orderId() {
        return UUID.randomUUID();
    }

    private Map<UUID, Integer> lines(String productId, int quantity) {
        Map<UUID, Integer> lines = new HashMap<>();
        lines.put(UUID.fromString(productId), quantity);
        return lines;
    }

    static class InMemorySagaRepository implements SagaProcessRepository {
        private final Map<UUID, SagaProcess> store = new HashMap<>();

        @Override
        public SagaProcess save(SagaProcess saga) {
            store.put(saga.sagaId(), saga);
            return saga;
        }

        @Override
        public Optional<SagaProcess> find(UUID sagaId) {
            return Optional.ofNullable(store.get(sagaId));
        }

        @Override
        public List<SagaProcess> findPending() {
            return store.values().stream()
                    .filter(saga -> !saga.status().isTerminal())
                    .toList();
        }

        List<SagaProcess> findAll() {
            return new ArrayList<>(store.values());
        }

        void clearPublisherCommands(RecordingCommandPublisher publisher) {
            publisher.commands.clear();
        }
    }

    static class RecordingCommandPublisher implements SagaCommandPublisher {
        final List<String> commands = new ArrayList<>();

        @Override
        public void sendInventoryReserve(UUID sagaId, UUID orderId, Map<UUID, Integer> lines) {
            commands.add("inventory.reserve:" + sagaId);
        }

        @Override
        public void sendInventoryRelease(UUID sagaId, UUID orderId) {
            commands.add("inventory.release:" + sagaId);
        }

        @Override
        public void sendFulfillmentShip(UUID sagaId, UUID orderId, Map<UUID, Integer> lines) {
            commands.add("fulfillment.ship:" + sagaId);
        }
    }
}