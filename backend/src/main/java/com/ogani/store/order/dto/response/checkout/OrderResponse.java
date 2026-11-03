package com.ogani.store.order.dto.response.checkout;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.ogani.store.order.entity.OrderStatus;
import com.ogani.store.order.entity.PaymentMethod;
import com.ogani.store.order.entity.PaymentStatus;

public record OrderResponse(Long id, String orderCode, String firstName, String lastName, String email,
        ShippingResponse deliveryAddress, String orderNotes, List<CheckoutLineResponse> items,
        BigDecimal subtotal, BigDecimal discountAmount, BigDecimal shippingFee, BigDecimal total,
        String currency, PaymentMethod paymentMethod, PaymentStatus paymentStatus,
        OrderStatus orderStatus, LocalDateTime createdAt) {}
