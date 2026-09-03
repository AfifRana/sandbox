package com.example.order.adapter.out.persistence;

import com.example.order.adapter.out.persistence.OrderEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOrderRepository extends JpaRepository<OrderEntity, UUID> {}
