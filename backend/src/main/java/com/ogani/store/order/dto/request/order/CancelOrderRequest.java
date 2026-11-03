package com.ogani.store.order.dto.request.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

public record CancelOrderRequest(@Size(max=200) String reason) {
    @JsonCreator
    public static CancelOrderRequest fromJson(@JsonProperty("reason") JsonNode reason) {
        if(reason==null || reason.isNull()) return new CancelOrderRequest(null);
        if(!reason.isString()) throw new IllegalArgumentException("reason must be a string");
        return new CancelOrderRequest(reason.stringValue());
    }
}
