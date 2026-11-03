package com.ogani.store.order.repository;

import com.ogani.store.order.entity.Order;
import com.ogani.store.order.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByUserIdAndCheckoutKey(Long userId, String checkoutKey);
    Optional<Order> findByIdAndUserId(Long id, Long userId);

    Page<OrderListView> findAllProjectedByUserId(Long userId, Pageable pageable);
    Page<OrderListView> findAllProjectedByUserIdAndOrderStatus(Long userId, OrderStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id=:id and o.user.id=:userId")
    Optional<Order> findOwnedByIdForUpdate(Long id, Long userId);

    @Query("""
            select o.id as id, o.orderCode as orderCode, o.version as version, o.user.id as userId,
                   o.firstName as firstName, o.lastName as lastName, o.email as email, o.phone as phone,
                   o.shipRecipientName as shipRecipientName, o.shipCountry as shipCountry,
                   o.shipAddress as shipAddress, o.shipCity as shipCity, o.shipState as shipState,
                   o.shipPostcode as shipPostcode, o.orderNotes as orderNotes,
                   o.subtotal as subtotal, o.discountAmount as discountAmount, o.shippingFee as shippingFee,
                   o.total as total, o.currency as currency, o.coupon.id as couponId, o.couponCode as couponCode,
                   o.paymentMethod as paymentMethod, o.paymentStatus as paymentStatus,
                   o.transactionRef as transactionRef, o.orderStatus as orderStatus,
                   o.createdAt as createdAt, o.updatedAt as updatedAt
            from Order o where o.id=:id
            """)
    Optional<AdminOrderDetailView> findAdminDetailById(Long id);
}
