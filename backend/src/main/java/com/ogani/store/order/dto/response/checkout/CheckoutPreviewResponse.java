package com.ogani.store.order.dto.response.checkout;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutPreviewResponse(List<CheckoutLineResponse> items, BigDecimal subtotal,
        BigDecimal discountAmount, BigDecimal shippingFee, BigDecimal total, String currency, String cartToken) {}
