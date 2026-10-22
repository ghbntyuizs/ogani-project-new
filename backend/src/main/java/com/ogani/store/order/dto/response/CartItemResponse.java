package com.ogani.store.order.dto.response;

import java.math.BigDecimal;

public record CartItemResponse(
        Long itemId,
        Long productId,
        String productName,
        String thumbnailUrl,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal
) {}
