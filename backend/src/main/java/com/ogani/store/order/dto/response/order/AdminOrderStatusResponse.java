package com.ogani.store.order.dto.response.order;

import com.ogani.store.order.entity.OrderStatus;
import com.ogani.store.order.entity.PaymentStatus;
import java.time.LocalDateTime;

public record AdminOrderStatusResponse(Long id,String orderCode,OrderStatus orderStatus,
        PaymentStatus paymentStatus,Long version,LocalDateTime updatedAt) {}
