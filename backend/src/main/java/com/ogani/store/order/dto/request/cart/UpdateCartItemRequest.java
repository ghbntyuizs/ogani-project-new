package com.ogani.store.order.dto.request.cart;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

public record UpdateCartItemRequest(@NotNull @PositiveOrZero Integer quantity) {
    @JsonCreator
    public static UpdateCartItemRequest fromJson(@JsonProperty("quantity") JsonNode quantity) {
        if (quantity == null || quantity.isNull()) return new UpdateCartItemRequest(null);
        if (!quantity.isIntegralNumber() || !quantity.canConvertToInt()) {
            throw new IllegalArgumentException("quantity must be a whole number within the supported range");
        }
        return new UpdateCartItemRequest(quantity.intValue());
    }
}
