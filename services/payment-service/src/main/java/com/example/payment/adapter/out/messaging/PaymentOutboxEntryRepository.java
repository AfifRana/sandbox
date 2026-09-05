package com.example.payment.adapter.out.messaging;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentOutboxEntryRepository extends JpaRepository<PaymentOutboxEntry, UUID> {

    List<PaymentOutboxEntry> findTop50ByPublishedFalseOrderByCreatedAtAsc();
}
