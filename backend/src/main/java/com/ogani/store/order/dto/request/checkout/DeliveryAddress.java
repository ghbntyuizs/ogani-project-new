package com.ogani.store.order.dto.request.checkout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeliveryAddress(
        @NotBlank @Size(max=20) String phone,
        @NotBlank @Size(max=60) String country,
        @NotBlank @Size(max=255) String address,
        @NotBlank @Size(max=80) String city,
        @Size(max=80) String state,
        @Size(max=20) String postcode) {}
