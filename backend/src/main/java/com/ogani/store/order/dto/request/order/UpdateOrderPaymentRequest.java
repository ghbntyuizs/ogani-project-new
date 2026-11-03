package com.ogani.store.order.dto.request.order;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ogani.store.order.entity.PaymentStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.JsonNode;

public record UpdateOrderPaymentRequest(@NotNull PaymentStatus paymentStatus,
                                       @NotNull @PositiveOrZero Long version) {
    @JsonCreator
    public static UpdateOrderPaymentRequest fromJson(@JsonProperty("paymentStatus") JsonNode status,
                                                     @JsonProperty("version") JsonNode version) {
        PaymentStatus target=null;
        Long revision=null;
        if(status!=null && !status.isNull()) {
            if(!status.isString()) throw new IllegalArgumentException("paymentStatus must be PAID");
            target=PaymentStatus.valueOf(status.stringValue());
            if(target!=PaymentStatus.PAID) throw new IllegalArgumentException("Only PAID is supported");
        }
        if(version!=null && !version.isNull()) {
            if(!version.isIntegralNumber() || !version.canConvertToLong()) throw new IllegalArgumentException("version must be a whole integer");
            revision=version.longValue();
        }
        return new UpdateOrderPaymentRequest(target,revision);
    }
}
