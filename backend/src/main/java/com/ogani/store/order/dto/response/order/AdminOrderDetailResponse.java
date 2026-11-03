package com.ogani.store.order.dto.response.order;

import com.ogani.store.order.dto.response.checkout.ShippingResponse;
import com.ogani.store.order.entity.OrderStatus;
import com.ogani.store.order.entity.PaymentMethod;
import com.ogani.store.order.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AdminOrderDetailResponse(Long id,String orderCode,Long version,Customer customer,List<Item> items,
        ShippingResponse deliveryAddress,Payment payment,Coupon coupon,BigDecimal subtotal,BigDecimal discountAmount,
        BigDecimal shippingFee,BigDecimal total,String currency,OrderStatus orderStatus,String orderNotes,
        List<History> statusHistory,LocalDateTime createdAt,LocalDateTime updatedAt) {
    public record Customer(Long userId,String firstName,String lastName,String email,String phone) {}
    public record Item(Long itemId,Long productId,String productName,BigDecimal unitPrice,Integer quantity,BigDecimal lineTotal) {}
    public record Payment(PaymentMethod method,PaymentStatus status,String transactionRef,LocalDateTime paidAt,Long confirmedBy) {}
    public record Coupon(Long id,String code) {}
    public record Actor(Long id,String name) {}
    public record History(Long id,String fromStatus,String toStatus,String note,Actor changedBy,LocalDateTime changedAt) {}
}
