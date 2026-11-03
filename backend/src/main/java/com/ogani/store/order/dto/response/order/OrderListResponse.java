package com.ogani.store.order.dto.response.order;

import com.ogani.store.order.entity.OrderStatus;
import com.ogani.store.order.entity.PaymentMethod;
import com.ogani.store.order.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderListResponse(Long id, String orderCode, OrderStatus orderStatus,
        PaymentStatus paymentStatus, PaymentMethod paymentMethod, BigDecimal total,
        String currency, LocalDateTime createdAt) {}
