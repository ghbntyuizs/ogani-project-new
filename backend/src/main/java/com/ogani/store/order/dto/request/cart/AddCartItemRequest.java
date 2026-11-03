package com.ogani.store.order.dto.request.cart;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import tools.jackson.databind.JsonNode;

public record AddCartItemRequest(
        @NotNull @Positive Long productId,
        @NotNull @Positive Integer quantity
) {
    // Reject fractional numbers and numeric strings instead of silently coercing them.
    @JsonCreator
    public static AddCartItemRequest fromJson(
            @JsonProperty("productId") JsonNode productId,
            @JsonProperty("quantity") JsonNode quantity) {
        Long id = null;
        Integer count = null;
        if (productId != null && !productId.isNull()) {
            if (!productId.isIntegralNumber() || !productId.canConvertToLong()) {
                throw new IllegalArgumentException("productId must be a whole number within the supported range");
            }
            id = productId.longValue();
        }
        if (quantity != null && !quantity.isNull()) {
            if (!quantity.isIntegralNumber() || !quantity.canConvertToInt()) {
                throw new IllegalArgumentException("quantity must be a whole number within the supported range");
            }
            count = quantity.intValue();
        }
        return new AddCartItemRequest(id, count);
    }
}
