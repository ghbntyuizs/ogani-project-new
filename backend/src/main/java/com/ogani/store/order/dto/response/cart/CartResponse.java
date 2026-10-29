package com.ogani.store.order.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        Long cartId,
        List<CartItemResponse> items,
        BigDecimal subtotal,
        String couponCode,
        BigDecimal discountAmount,
        BigDecimal shippingFee,
        BigDecimal total
) {}
