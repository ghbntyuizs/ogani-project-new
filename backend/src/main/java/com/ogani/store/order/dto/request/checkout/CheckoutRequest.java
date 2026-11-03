package com.ogani.store.order.dto.request.checkout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.ogani.store.order.entity.PaymentMethod;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

public record CheckoutRequest(
        @NotBlank @Pattern(regexp="[0-9a-f]{64}") String cartToken,
        @NotBlank @Size(max=60) String firstName,
        @NotBlank @Size(max=60) String lastName,
        @NotBlank @Email @Size(max=150) String email,
        @NotNull PaymentMethod paymentMethod,
        @Positive Long addressId,
        @Valid DeliveryAddress deliveryAddress,
        @Size(max=2000) String orderNotes,
        @Size(max=30) String couponCode) {
    @JsonCreator
    public static CheckoutRequest fromJson(
            @JsonProperty("cartToken") String cartToken,
            @JsonProperty("firstName") String firstName,
            @JsonProperty("lastName") String lastName,
            @JsonProperty("email") String email,
            @JsonProperty("paymentMethod") JsonNode paymentMethod,
            @JsonProperty("addressId") JsonNode addressId,
            @JsonProperty("deliveryAddress") DeliveryAddress deliveryAddress,
            @JsonProperty("orderNotes") String orderNotes,
            @JsonProperty("couponCode") String couponCode) {
        Long id=null;
        if (addressId!=null && !addressId.isNull()) {
            if (!addressId.isIntegralNumber() || !addressId.canConvertToLong()) throw new IllegalArgumentException("addressId must be a whole integer");
            id=addressId.longValue();
        }
        PaymentMethod method=null;
        if (paymentMethod!=null && !paymentMethod.isNull()) {
            if (!paymentMethod.isString()) throw new IllegalArgumentException("paymentMethod must be a supported name");
            method=PaymentMethod.valueOf(paymentMethod.stringValue());
        }
        return new CheckoutRequest(cartToken,firstName,lastName,email,method,id,deliveryAddress,orderNotes,couponCode);
    }
}
