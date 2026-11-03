package com.ogani.store.order.repository;

import com.ogani.store.order.entity.Order;
import com.ogani.store.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByUserIdAndCheckoutKey(Long userId, String checkoutKey);
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Page<OrderListView> findAllProjectedByUserId(Long userId, Pageable pageable);
    Page<OrderListView> findAllProjectedByUserIdAndOrderStatus(Long userId, OrderStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id=:id and o.user.id=:userId")
    Optional<Order> findOwnedByIdForUpdate(Long id, Long userId);
}
