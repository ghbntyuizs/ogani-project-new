package com.ogani.store.order.repository;

import com.ogani.store.order.entity.OrderStatus;
import com.ogani.store.order.entity.PaymentMethod;
import com.ogani.store.order.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface OrderListView {
    Long getId();
    String getOrderCode();
    OrderStatus getOrderStatus();
    PaymentStatus getPaymentStatus();
    PaymentMethod getPaymentMethod();
    BigDecimal getTotal();
    String getCurrency();
    LocalDateTime getCreatedAt();
}
