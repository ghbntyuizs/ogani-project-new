package com.ogani.store.order.repository;

import com.ogani.store.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByUserIdAndCheckoutKey(Long userId, String checkoutKey);
    Optional<Order> findByIdAndUserId(Long id, Long userId);
}
