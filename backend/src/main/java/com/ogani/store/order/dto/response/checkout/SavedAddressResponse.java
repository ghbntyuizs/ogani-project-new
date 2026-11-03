package com.ogani.store.order.dto.response.checkout;

public record SavedAddressResponse(Long id, String recipientName, String phone, String country,
        String address, String city, String state, String postcode, boolean defaultAddress,String district,String ward) {}
