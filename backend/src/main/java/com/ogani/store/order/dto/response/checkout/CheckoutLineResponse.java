package com.ogani.store.order.dto.response.checkout;

import java.math.BigDecimal;

public record CheckoutLineResponse(Long productId, String productName, BigDecimal unitPrice,
                                   Integer quantity, BigDecimal lineTotal) {}
