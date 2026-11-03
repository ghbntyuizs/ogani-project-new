package com.ogani.store.order.dto.response.checkout;

public record ShippingResponse(String recipientName, String phone, String country,
                               String address, String city, String state, String postcode,String district,String ward) {}
