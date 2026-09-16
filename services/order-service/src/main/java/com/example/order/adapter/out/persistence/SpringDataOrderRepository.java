package com.example.order.adapter.out.persistence;

import com.example.order.adapter.out.persistence.OrderEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
}
